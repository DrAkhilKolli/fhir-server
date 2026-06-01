/*
 * (C) Copyright Clinivault Inc. 2024, 2026
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.clinivault.fhir.interceptors;

import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.linuxforhealth.fhir.config.FHIRRequestContext;
import org.linuxforhealth.fhir.persistence.context.FHIRPersistenceEvent;
import org.linuxforhealth.fhir.server.spi.interceptor.FHIRPersistenceInterceptor;
import org.linuxforhealth.fhir.server.spi.interceptor.FHIRPersistenceInterceptorException;

import jakarta.json.JsonObject;

/**
 * Enforces SMART-on-FHIR scope requirements on every FHIR operation.
 *
 * <p>The scope claim is extracted from the Bearer JWT (via {@link JwtUtils}).
 * Each operation type is mapped to the minimum required scope using
 * a hierarchical check (system &gt; user &gt; patient precedence).
 *
 * <table border="1">
 *   <caption>Scope → operations mapping</caption>
 *   <tr><th>Scope</th><th>Allowed operations</th></tr>
 *   <tr><td>{@code system/*.read}</td><td>read, vread, history, search</td></tr>
 *   <tr><td>{@code user/*.read}</td><td>read, vread, history, search</td></tr>
 *   <tr><td>{@code patient/*.read}</td><td>read, vread, history, search</td></tr>
 *   <tr><td>{@code system/*.write}</td><td>create, update, delete</td></tr>
 *   <tr><td>{@code user/*.write}</td><td>create, update, delete</td></tr>
 *   <tr><td>{@code patient/*.write}</td><td>create, update, delete</td></tr>
 * </table>
 */
public class SmartScopeEnforcementInterceptor implements FHIRPersistenceInterceptor {

    private static final Logger LOG = Logger.getLogger(SmartScopeEnforcementInterceptor.class.getName());

    /** Scope suffixes that grant read access to any resource type. */
    private static final Set<String> READ_WILDCARD_SCOPES = Set.of(
        "system/*.read", "user/*.read", "patient/*.read"
    );

    /** Scope suffixes that grant write access to any resource type. */
    private static final Set<String> WRITE_WILDCARD_SCOPES = Set.of(
        "system/*.write", "user/*.write", "patient/*.write"
    );

    // --------------------------------------------------------------------- //
    //  Read-class operations
    // --------------------------------------------------------------------- //

    @Override
    public void beforeRead(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        enforceReadScope(event, "read");
    }

    @Override
    public void beforeVread(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        enforceReadScope(event, "vread");
    }

    @Override
    public void beforeHistory(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        enforceReadScope(event, "history");
    }

    @Override
    public void beforeSearch(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        enforceReadScope(event, "search");
    }

    // --------------------------------------------------------------------- //
    //  Write-class operations
    // --------------------------------------------------------------------- //

    @Override
    public void beforeCreate(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        enforceWriteScope(event, "create");
    }

    @Override
    public void beforeUpdate(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        enforceWriteScope(event, "update");
    }

    @Override
    public void beforeDelete(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        enforceWriteScope(event, "delete");
    }

    // --------------------------------------------------------------------- //
    //  Enforcement helpers
    // --------------------------------------------------------------------- //

    private void enforceReadScope(FHIRPersistenceEvent event, String operationName)
            throws FHIRPersistenceInterceptorException {
        Set<String> tokenScopes = extractScopes();
        String resourceType = getResourceType(event);

        if (hasWildcardScope(tokenScopes, READ_WILDCARD_SCOPES)) {
            return;
        }
        if (hasResourceSpecificScope(tokenScopes, resourceType, "read")) {
            return;
        }
        LOG.warning(
            "SmartScopeEnforcementInterceptor: access denied for "
            + operationName + " on " + resourceType
            + "; token scopes=" + tokenScopes
        );
        throw new FHIRPersistenceInterceptorException(
            "SMART scope enforcement: insufficient scope for " + operationName
            + " on " + resourceType + ". Requires patient/*.read, user/*.read, or system/*.read."
        );
    }

    private void enforceWriteScope(FHIRPersistenceEvent event, String operationName)
            throws FHIRPersistenceInterceptorException {
        Set<String> tokenScopes = extractScopes();
        String resourceType = getResourceType(event);

        if (hasWildcardScope(tokenScopes, WRITE_WILDCARD_SCOPES)) {
            return;
        }
        if (hasResourceSpecificScope(tokenScopes, resourceType, "write")) {
            return;
        }
        LOG.warning(
            "SmartScopeEnforcementInterceptor: write access denied for "
            + operationName + " on " + resourceType
            + "; token scopes=" + tokenScopes
        );
        throw new FHIRPersistenceInterceptorException(
            "SMART scope enforcement: insufficient scope for " + operationName
            + " on " + resourceType + ". Requires patient/*.write, user/*.write, or system/*.write."
        );
    }

    /**
     * Extract the {@code scope} claim from the Bearer JWT as a set of individual
     * scope strings.  Returns an empty set if the claim is absent (fail-closed
     * callers must treat an empty set as a denial).
     */
    private Set<String> extractScopes() {
        JsonObject claims = JwtUtils.extractClaims();
        if (claims == null) {
            return Set.of();
        }
        String scopeStr = claims.getString("scope", "");
        if (scopeStr.isBlank()) {
            return Set.of();
        }
        return Set.of(scopeStr.split("\\s+"));
    }

    /** Returns {@code true} if tokenScopes contains any of the wildcardScopes. */
    private boolean hasWildcardScope(Set<String> tokenScopes, Set<String> wildcardScopes) {
        for (String ws : wildcardScopes) {
            if (tokenScopes.contains(ws)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks for resource-type-specific scopes of the form
     * {@code patient/Patient.read}, {@code user/Observation.read}, etc.
     */
    private boolean hasResourceSpecificScope(Set<String> tokenScopes, String resourceType,
                                              String accessType) {
        String patientSpecific = "patient/" + resourceType + "." + accessType;
        String userSpecific    = "user/"    + resourceType + "." + accessType;
        String systemSpecific  = "system/"  + resourceType + "." + accessType;
        return tokenScopes.contains(patientSpecific)
            || tokenScopes.contains(userSpecific)
            || tokenScopes.contains(systemSpecific);
    }

    private String getResourceType(FHIRPersistenceEvent event) {
        if (event == null) {
            return "Unknown";
        }
        Object resource = event.getFhirResource();
        if (resource != null) {
            // LinuxForHealth FHIR resources expose getClass().getSimpleName()
            return resource.getClass().getSimpleName();
        }
        return "Unknown";
    }
}
