/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * FHIRTenantKeycloakInterceptor
 * ==============================
 * A FHIRPersistenceInterceptor that enforces the following for every request:
 *
 *  1. Tenant/issuer binding
 *     The JWT `iss` claim must match the Keycloak realm URL configured for
 *     the current X-FHIR-TENANT-ID in tenant-registry.json.
 *
 *  2. Required claims
 *     The JWT must carry:
 *       - `fhirUser`       — canonical FHIR resource reference (e.g. Practitioner/abc)
 *       - `organization_id` — organisation the user belongs to
 *
 *  3. RBAC
 *     The `groups` claim must include at least one of the roles permitted for
 *     the requested operation.  Write operations require /fhir-admins or
 *     /fhir-users; read/search require /fhir-users.
 *
 *  4. ABAC (Patient compartment enforcement)
 *     When fhirUser is a Patient reference the interceptor rewrites search
 *     requests to add a `patient` compartment filter so that users can only
 *     see their own data.
 *
 * The tenant registry is loaded once from
 *   ${server.config.dir}/tenant-registry.json
 * (placed there by config-sync.sh at container startup) and cached in a
 * static map.  A SIGHUP / container restart reloads it.
 */

package org.linuxforhealth.fhir.keycloak;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.linuxforhealth.fhir.config.FHIRRequestContext;
import org.linuxforhealth.fhir.model.type.code.IssueType;
import org.linuxforhealth.fhir.model.util.FHIRUtil;
import org.linuxforhealth.fhir.persistence.context.FHIRPersistenceEvent;
import org.linuxforhealth.fhir.search.compartment.CompartmentHelper;
import org.linuxforhealth.fhir.search.context.FHIRSearchContext;
import org.linuxforhealth.fhir.search.parameters.QueryParameter;
import org.linuxforhealth.fhir.search.util.SearchHelper;
import org.linuxforhealth.fhir.server.spi.interceptor.FHIRPersistenceInterceptor;
import org.linuxforhealth.fhir.server.spi.interceptor.FHIRPersistenceInterceptorException;
import org.linuxforhealth.fhir.smart.JWT;
import org.linuxforhealth.fhir.smart.JWT.DecodedJWT;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

/**
 * Keycloak tenant/issuer binding + RBAC/ABAC persistence interceptor.
 */
public class FHIRTenantKeycloakInterceptor implements FHIRPersistenceInterceptor {

    private static final Logger LOG = Logger.getLogger(FHIRTenantKeycloakInterceptor.class.getName());

    private static final String BEARER_PREFIX = "Bearer";
    private static final String PATIENT = "Patient";

    // Roles recognised for RBAC checks (Keycloak group names)
    private static final String ROLE_FHIR_USERS  = "/fhir-users";
    private static final String ROLE_FHIR_ADMINS = "/fhir-admins";

    /** tenantId → expected Keycloak issuer URL */
    private static volatile Map<String, String> TENANT_ISSUER_MAP = null;
    private static final JsonReaderFactory JSON_READER_FACTORY = Json.createReaderFactory(null);

    private final CompartmentHelper compartmentHelper = new CompartmentHelper();
    private final SearchHelper searchHelper = new SearchHelper();

    // -------------------------------------------------------------------------
    // Registry loading
    // -------------------------------------------------------------------------

    /**
     * Returns the tenant → issuer mapping, loading and caching it on first call.
     * Call refreshTenantRegistry() to force a reload (e.g. after config-sync).
     */
    private static Map<String, String> getTenantIssuerMap() {
        if (TENANT_ISSUER_MAP == null) {
            synchronized (FHIRTenantKeycloakInterceptor.class) {
                if (TENANT_ISSUER_MAP == null) {
                    TENANT_ISSUER_MAP = loadTenantRegistry();
                }
            }
        }
        return TENANT_ISSUER_MAP;
    }

    /** Force-reload the tenant registry from disk. */
    public static synchronized void refreshTenantRegistry() {
        TENANT_ISSUER_MAP = loadTenantRegistry();
    }

    private static Map<String, String> loadTenantRegistry() {
        // Liberty server.config.dir is exposed as system property on OpenLiberty
        String configDir = System.getProperty("server.config.dir",
                                              "/opt/ol/wlp/usr/servers/defaultServer");
        Path registryPath = Paths.get(configDir, "tenant-registry.json");

        if (!Files.exists(registryPath)) {
            LOG.warning("tenant-registry.json not found at " + registryPath
                    + " — issuer validation disabled until file is present.");
            return Collections.emptyMap();
        }

        Map<String, String> map = new HashMap<>();
        try (InputStream is = Files.newInputStream(registryPath)) {
            JsonReader reader = JSON_READER_FACTORY.createReader(is);
            JsonObject root = reader.readObject();
            JsonArray tenants = root.getJsonArray("tenants");
            if (tenants != null) {
                for (JsonValue entry : tenants) {
                    JsonObject t = entry.asJsonObject();
                    String tenantId        = t.getString("tenantId", null);
                    String realmUrl        = t.getString("keycloakRealmUrl", null);
                    boolean enabled        = t.getBoolean("enabled", true);
                    if (tenantId != null && realmUrl != null && enabled) {
                        map.put(tenantId, realmUrl);
                    }
                }
            }
            LOG.info("Loaded tenant registry: " + map.size() + " tenant(s) from " + registryPath);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to load tenant-registry.json", e);
        }
        return Collections.unmodifiableMap(map);
    }

    // -------------------------------------------------------------------------
    // JWT extraction helper
    // -------------------------------------------------------------------------

    private String getAccessToken() throws FHIRPersistenceInterceptorException {
        List<String> list = FHIRRequestContext.get().getHttpHeaders().get("Authorization");
        if (list == null || list.isEmpty()) {
            deny("Request is missing the Authorization header", IssueType.FORBIDDEN);
        }
        String header = list.get(0);
        if (!header.startsWith(BEARER_PREFIX)) {
            deny("Authorization header must carry a Bearer token", IssueType.FORBIDDEN);
        }
        return header.substring(BEARER_PREFIX.length()).trim();
    }

    // -------------------------------------------------------------------------
    // Core validation — called at the start of every write/read operation
    // -------------------------------------------------------------------------

    /**
     * Validates issuer binding + required claims.  Returns the decoded JWT so
     * callers can continue with RBAC/ABAC checks.
     */
    private DecodedJWT validateRequest() throws FHIRPersistenceInterceptorException {
        String tenantId = FHIRRequestContext.get().getTenantId();
        DecodedJWT jwt  = JWT.decode(getAccessToken());

        // 1. Issuer binding
        String expectedIssuer = getTenantIssuerMap().get(tenantId);
        if (expectedIssuer != null) {
            String actualIssuer = jwt.getClaim("iss").asString();
            if (actualIssuer == null || !actualIssuer.equals(expectedIssuer)) {
                deny("JWT issuer '" + actualIssuer + "' does not match expected issuer '"
                        + expectedIssuer + "' for tenant '" + tenantId + "'", IssueType.FORBIDDEN);
            }
        } else {
            LOG.fine("No issuer mapping for tenant '" + tenantId
                    + "'; skipping issuer binding check (tenant not in registry)");
        }

        // 2. Required claim: fhirUser
        String fhirUser = jwt.getClaim("fhirUser").asString();
        if (fhirUser == null || fhirUser.isBlank()) {
            deny("JWT is missing required 'fhirUser' claim", IssueType.FORBIDDEN);
        }
        if (!fhirUser.contains("/")) {
            deny("'fhirUser' claim must be a FHIR reference (ResourceType/id), got: " + fhirUser,
                    IssueType.FORBIDDEN);
        }

        // 3. Required claim: organization_id
        String orgId = jwt.getClaim("organization_id").asString();
        if (orgId == null || orgId.isBlank()) {
            deny("JWT is missing required 'organization_id' claim", IssueType.FORBIDDEN);
        }

        return jwt;
    }

    // -------------------------------------------------------------------------
    // RBAC helper
    // -------------------------------------------------------------------------

    private void checkRole(DecodedJWT jwt, boolean writeOperation) throws FHIRPersistenceInterceptorException {
        List<String> groups = jwt.getClaim("groups").asList();
        if (groups == null) {
            deny("JWT 'groups' claim is absent — user has no FHIR role", IssueType.FORBIDDEN);
        }

        boolean hasUserRole  = groups.contains(ROLE_FHIR_USERS)  || groups.contains(ROLE_FHIR_ADMINS);
        boolean hasAdminRole = groups.contains(ROLE_FHIR_ADMINS);

        if (writeOperation && !hasAdminRole && !groups.contains(ROLE_FHIR_USERS)) {
            deny("Write operation requires role " + ROLE_FHIR_USERS + " or " + ROLE_FHIR_ADMINS,
                    IssueType.FORBIDDEN);
        }
        if (!hasUserRole) {
            deny("User does not have a recognised FHIR role", IssueType.FORBIDDEN);
        }
    }

    // -------------------------------------------------------------------------
    // ABAC helper — Patient compartment enforcement
    // -------------------------------------------------------------------------

    /**
     * If fhirUser is a Patient, add a compartment inclusion criterion so the search
     * is automatically scoped to that patient's compartment — mirrors fhir-smart logic.
     */
    private void enforcePatientCompartment(DecodedJWT jwt, FHIRPersistenceEvent event)
            throws FHIRPersistenceInterceptorException {
        FHIRSearchContext searchContext = event.getSearchContextImpl();
        if (searchContext == null) return;

        String fhirUser = jwt.getClaim("fhirUser").asString();
        if (fhirUser == null) return;

        String[] parts = fhirUser.split("/", 2);
        if (parts.length != 2 || !PATIENT.equals(parts[0])) return;

        String patientId   = parts[1];
        String resourceType = event.getFhirResourceType();

        try {
            if (compartmentHelper.getCompartmentResourceTypes(PATIENT).contains(resourceType)) {
                Set<String> patientIds = java.util.Collections.singleton(patientId);
                QueryParameter inclusionCriteria =
                        searchHelper.buildInclusionCriteria(PATIENT, patientIds, resourceType);
                searchContext.getSearchParameters().add(0, inclusionCriteria);
                LOG.fine("ABAC: scoped search for resource type '" + resourceType
                        + "' to Patient compartment of " + fhirUser);
            }
        } catch (Exception e) {
            String msg = "ABAC: failed to add Patient compartment restriction: " + e.getMessage();
            throw new FHIRPersistenceInterceptorException(msg)
                    .withIssue(FHIRUtil.buildOperationOutcomeIssue(msg, IssueType.EXCEPTION));
        }
    }

    // -------------------------------------------------------------------------
    // FHIRPersistenceInterceptor — lifecycle hooks
    // -------------------------------------------------------------------------

    @Override
    public void beforeCreate(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        DecodedJWT jwt = validateRequest();
        checkRole(jwt, true);
    }

    @Override
    public void beforeUpdate(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        DecodedJWT jwt = validateRequest();
        checkRole(jwt, true);
    }

    @Override
    public void beforeDelete(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        DecodedJWT jwt = validateRequest();
        // Only FHIRAdmins may delete resources
        List<String> groups = jwt.getClaim("groups").asList();
        if (groups == null || !groups.contains(ROLE_FHIR_ADMINS)) {
            deny("Delete requires role " + ROLE_FHIR_ADMINS, IssueType.FORBIDDEN);
        }
    }

    @Override
    public void beforeRead(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        DecodedJWT jwt = validateRequest();
        checkRole(jwt, false);
    }

    @Override
    public void beforeSearch(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        DecodedJWT jwt = validateRequest();
        checkRole(jwt, false);
        // ABAC: patient-context users see only their own compartment
        enforcePatientCompartment(jwt, event);
    }

    @Override
    public void beforePatch(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        DecodedJWT jwt = validateRequest();
        checkRole(jwt, true);
    }

    // -------------------------------------------------------------------------
    // Error helper
    // -------------------------------------------------------------------------

    private static void deny(String message, IssueType issueType)
            throws FHIRPersistenceInterceptorException {
        LOG.warning("Access denied: " + message);
        throw new FHIRPersistenceInterceptorException(message)
                .withIssue(FHIRUtil.buildOperationOutcomeIssue(message, issueType));
    }
}
