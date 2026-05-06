/*
 * (C) Copyright IBM Corp. 2026
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.linuxforhealth.fhir.smart.test;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.linuxforhealth.fhir.config.FHIRConfiguration;
import org.linuxforhealth.fhir.config.FHIRRequestContext;
import org.linuxforhealth.fhir.model.resource.Observation;
import org.linuxforhealth.fhir.model.test.TestUtil;
import org.linuxforhealth.fhir.model.type.Code;
import org.linuxforhealth.fhir.model.type.Coding;
import org.linuxforhealth.fhir.model.type.Id;
import org.linuxforhealth.fhir.model.type.Meta;
import org.linuxforhealth.fhir.model.type.Reference;
import org.linuxforhealth.fhir.model.type.Uri;
import org.linuxforhealth.fhir.server.spi.interceptor.FHIRPersistenceInterceptorException;
import org.linuxforhealth.fhir.smart.AuthzPolicyEnforcementPersistenceInterceptor;

public class AuthzAbacFocusedTest {
    private static final String OBSERVATION_ID = "abac-obs-1";
    private static final String TENANT_SYSTEM = "https://linuxforhealth.org/fhir/abac/tenant";
    private static final String ORG_SYSTEM = "https://linuxforhealth.org/fhir/abac/org";

    private static final String ABAC_ALLOWED_PURPOSES_PROPERTY = "fhirServer/security/oauth/smart/abac/allowedPurposes";
    private static final String ABAC_RESOURCE_TENANT_SYSTEM_PROPERTY = "fhirServer/security/oauth/smart/abac/resourceTenantSystem";
    private static final String ABAC_RESOURCE_ORG_SYSTEM_PROPERTY = "fhirServer/security/oauth/smart/abac/resourceOrgSystem";

    private static final String CLAIM_TENANT_ID = "tenant_id";
    private static final String CLAIM_ORG_ID = "org_id";

    private AuthzPolicyEnforcementPersistenceInterceptor interceptor;
    private Observation labeledObservation;
    private Observation unlabeledObservation;

    private String originalConfigHome;
    private Path tempConfigHome;

    @BeforeClass
    public void setup() throws Exception {
        FHIRRequestContext.set(new FHIRRequestContext("default"));
        interceptor = new AuthzPolicyEnforcementPersistenceInterceptor();

        Observation base = TestUtil.getMinimalResource(Observation.class).toBuilder()
                .id(OBSERVATION_ID)
                .subject(Reference.builder().reference(org.linuxforhealth.fhir.model.type.String.string("Patient/abac-patient")).build())
                .build();

        Coding tenantTag = Coding.builder().system(Uri.of(TENANT_SYSTEM)).code(Code.of("tenant-a")).build();
        Coding orgTag = Coding.builder().system(Uri.of(ORG_SYSTEM)).code(Code.of("org-001")).build();

        labeledObservation = base.toBuilder()
                .meta(Meta.builder().versionId(Id.of("1")).tag(tenantTag).tag(orgTag).build())
                .build();

        unlabeledObservation = base.toBuilder()
                .meta(Meta.builder().versionId(Id.of("1")).build())
                .build();

        originalConfigHome = FHIRConfiguration.getConfigHome();
    }

    @AfterClass
    public void cleanup() throws Exception {
        FHIRConfiguration.setConfigHome(originalConfigHome);
        FHIRConfiguration.getInstance().clearConfiguration();
        if (tempConfigHome != null && Files.exists(tempConfigHome)) {
            try (var stream = Files.walk(tempConfigHome)) {
                stream.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    @Test
    public void tenantOrgMatchAllowPath() throws Exception {
        org.linuxforhealth.fhir.smart.JWT.DecodedJWT jwt = decodeJwt("user/Observation.read", "tenant-a", "org-001", "TREAT");

        invokePrivate(
            "assertClaimMatchesResource",
            new Class<?>[] { String.class, String.class, org.linuxforhealth.fhir.model.resource.Resource.class,
                    org.linuxforhealth.fhir.smart.JWT.DecodedJWT.class, String.class, String.class, String.class,
                    boolean.class },
            "Observation", OBSERVATION_ID, labeledObservation, jwt, CLAIM_TENANT_ID,
            ABAC_RESOURCE_TENANT_SYSTEM_PROPERTY, TENANT_SYSTEM, true);

        invokePrivate(
            "assertClaimMatchesResource",
            new Class<?>[] { String.class, String.class, org.linuxforhealth.fhir.model.resource.Resource.class,
                    org.linuxforhealth.fhir.smart.JWT.DecodedJWT.class, String.class, String.class, String.class,
                    boolean.class },
            "Observation", OBSERVATION_ID, labeledObservation, jwt, CLAIM_ORG_ID,
            ABAC_RESOURCE_ORG_SYSTEM_PROPERTY, ORG_SYSTEM, true);
    }

    @Test
    public void missingLabelsDenyPath() throws Exception {
        org.linuxforhealth.fhir.smart.JWT.DecodedJWT jwt = decodeJwt("user/Observation.read", "tenant-a", "org-001", "TREAT");

        try {
            invokePrivate(
                "assertClaimMatchesResource",
                new Class<?>[] { String.class, String.class, org.linuxforhealth.fhir.model.resource.Resource.class,
                        org.linuxforhealth.fhir.smart.JWT.DecodedJWT.class, String.class, String.class, String.class,
                        boolean.class },
                "Observation", OBSERVATION_ID, unlabeledObservation, jwt, CLAIM_TENANT_ID,
                ABAC_RESOURCE_TENANT_SYSTEM_PROPERTY, TENANT_SYSTEM, true);
            fail("Expected ABAC deny for missing resource labels");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            assertTrue(cause instanceof FHIRPersistenceInterceptorException, String.valueOf(cause));
            assertTrue(cause.getMessage().contains("missing required label system"), cause.getMessage());
        }
    }

    @Test
    public void purposeAllowListDenyPath() throws Exception {
        configureAllowedPurposes("TREAT");
        org.linuxforhealth.fhir.smart.JWT.DecodedJWT jwt = decodeJwt("user/Observation.read", "tenant-a", "org-001", "RESEARCH");

        try {
            invokePrivate(
                "validatePurposeOfUse",
                new Class<?>[] { org.linuxforhealth.fhir.smart.JWT.DecodedJWT.class },
                jwt);
            fail("Expected ABAC deny for purpose_of_use not in allow-list");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            assertTrue(cause instanceof FHIRPersistenceInterceptorException, String.valueOf(cause));
            assertTrue(cause.getMessage().contains("purpose_of_use is not allowed"), cause.getMessage());
        }
    }

    @Test
    public void abacDisabledCompatibilityPath() throws Exception {
        // ABAC is disabled by default in config, so this should be a no-op even with unlabeled resource.
        org.linuxforhealth.fhir.smart.JWT.DecodedJWT jwt = decodeJwt("user/Observation.read", "wrong-tenant", "wrong-org", "RESEARCH");

        invokePrivate(
            "enforceAbacRead",
            new Class<?>[] { String.class, String.class, org.linuxforhealth.fhir.model.resource.Resource.class,
                    org.linuxforhealth.fhir.smart.JWT.DecodedJWT.class },
            "Observation", OBSERVATION_ID, unlabeledObservation, jwt);
    }

    private org.linuxforhealth.fhir.smart.JWT.DecodedJWT decodeJwt(String scope, String tenantId, String orgId, String purposeOfUse) {
        String token = JWT.create()
                .withClaim("scope", scope)
                .withClaim("tenant_id", tenantId)
                .withClaim("org_id", orgId)
                .withClaim("purpose_of_use", purposeOfUse)
                .sign(Algorithm.none());
        return org.linuxforhealth.fhir.smart.JWT.decode(token);
    }

    private Object invokePrivate(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = AuthzPolicyEnforcementPersistenceInterceptor.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(interceptor, args);
    }

    private void configureAllowedPurposes(String allowedPurposes) throws Exception {
        if (tempConfigHome != null && Files.exists(tempConfigHome)) {
            try (var stream = Files.walk(tempConfigHome)) {
                stream.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }

        tempConfigHome = Files.createTempDirectory("fhir-abac-purpose-config-");
        Path targetConfigDir = tempConfigHome.resolve(Path.of("config", "default"));
        Files.createDirectories(targetConfigDir);

        String minimalConfig = "{\n"
                + "  \"fhirServer\": {\n"
                + "    \"security\": {\n"
                + "      \"oauth\": {\n"
                + "        \"smart\": {\n"
                + "          \"abac\": {\n"
                + "            \"allowedPurposes\": \"" + allowedPurposes + "\"\n"
                + "          }\n"
                + "        }\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}\n";

        Files.writeString(targetConfigDir.resolve("fhir-server-config.json"), minimalConfig, StandardCharsets.UTF_8);
        FHIRConfiguration.setConfigHome(tempConfigHome.toString());
        FHIRConfiguration.getInstance().clearConfiguration();
        FHIRRequestContext.set(new FHIRRequestContext("default"));

        // Ensure other tests continue to use default config after this one unless explicitly overridden.
        // The test method calling this helper runs in a fresh JVM fork for surefire class execution.
    }
}
