/*
 * (C) Copyright Clinivault Inc. 2024, 2026
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.clinivault.fhir.interceptors;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.linuxforhealth.fhir.config.FHIRRequestContext;
import org.linuxforhealth.fhir.model.resource.Provenance;
import org.linuxforhealth.fhir.model.resource.Resource;
import org.linuxforhealth.fhir.model.type.Canonical;
import org.linuxforhealth.fhir.model.type.Code;
import org.linuxforhealth.fhir.model.type.CodeableConcept;
import org.linuxforhealth.fhir.model.type.Coding;
import org.linuxforhealth.fhir.model.type.Extension;
import org.linuxforhealth.fhir.model.type.Instant;
import org.linuxforhealth.fhir.model.type.Reference;
import org.linuxforhealth.fhir.model.type.Uri;
import org.linuxforhealth.fhir.model.type.code.ProvenanceEntityRole;
import org.linuxforhealth.fhir.persistence.FHIRPersistenceException;
import org.linuxforhealth.fhir.persistence.SingleResourceResult;
import org.linuxforhealth.fhir.persistence.context.FHIRPersistenceContext;
import org.linuxforhealth.fhir.persistence.context.FHIRPersistenceEvent;
import org.linuxforhealth.fhir.server.spi.interceptor.FHIRPersistenceInterceptor;
import org.linuxforhealth.fhir.server.spi.interceptor.FHIRPersistenceInterceptorException;

import jakarta.json.JsonObject;
import jakarta.json.JsonString;

/**
 * Generates a FHIR R4 {@link Provenance} resource after every successful
 * create or update operation. The Provenance record:
 *
 * <ul>
 *   <li>targets the newly persisted resource</li>
 *   <li>records the wall-clock timestamp as {@code recorded}</li>
 *   <li>identifies the agent from the JWT {@code fhirUser} claim (for human
 *       practitioners) or the {@code agent_id} claim (for AI service accounts)</li>
 *   <li>carries a Clinivault tenant extension for multi-tenant traceability</li>
 * </ul>
 *
 * <p>The interceptor runs <em>after</em> persistence so that the primary
 * resource is always saved even if Provenance creation fails.  Failures are
 * logged at WARNING level and swallowed — they never block the calling
 * thread.
 *
 * <p>Registration: listed in
 * {@code META-INF/services/org.linuxforhealth.fhir.server.spi.interceptor.FHIRPersistenceInterceptor}.
 */
public class ProvenanceGenerationInterceptor implements FHIRPersistenceInterceptor {

    private static final Logger LOG = Logger.getLogger(
            ProvenanceGenerationInterceptor.class.getName());

    // FHIR coding systems
    private static final String DATA_OPERATION_SYSTEM =
            "http://terminology.hl7.org/CodeSystem/v3-DataOperation";
    private static final String PARTICIPANT_TYPE_SYSTEM =
            "http://terminology.hl7.org/CodeSystem/provenance-participant-type";

    // Clinivault extension URL for tenant identity
    private static final String TENANT_ID_EXTENSION_URL =
            "https://clinivault.io/fhir/StructureDefinition/tenant-id";

    // Claim names we look for in the JWT
    private static final String CLAIM_FHIR_USER  = "fhirUser";
    private static final String CLAIM_AGENT_ID   = "agent_id";
    private static final String CLAIM_TENANT_ID  = "tenant_id";
    private static final String CLAIM_PRACTITIONER_ID = "practitioner_id";

    // -----------------------------------------------------------------------
    // FHIRPersistenceInterceptor hooks
    // -----------------------------------------------------------------------

    @Override
    public void afterCreate(FHIRPersistenceEvent event)
            throws FHIRPersistenceInterceptorException {
        recordProvenance(event, "CREATE", "create");
    }

    @Override
    public void afterUpdate(FHIRPersistenceEvent event)
            throws FHIRPersistenceInterceptorException {
        recordProvenance(event, "UPDATE", "revise");
    }

    // -----------------------------------------------------------------------
    // Provenance construction
    // -----------------------------------------------------------------------

    /**
     * Build and persist a Provenance resource that documents the originating
     * operation.
     *
     * @param event       the persistence event carrying the newly persisted resource
     * @param activityCode FHIR DataOperation code (CREATE / UPDATE)
     * @param activityDisplay human-readable label
     */
    private void recordProvenance(
            FHIRPersistenceEvent event,
            String activityCode,
            String activityDisplay) {

        try {
            Resource resource = event.getFhirResource();
            if (resource == null) {
                return;
            }

            // Skip generating Provenance for Provenance resources to avoid loops
            if (resource instanceof Provenance) {
                return;
            }

            String resourceType = resource.getClass().getSimpleName();
            String resourceId   = resource.getId();
            if (resourceId == null || resourceId.isEmpty()) {
                // Resource has no id yet — cannot create a meaningful target reference
                return;
            }

            String tenantId = resolveTenantId();
            JsonObject claims = JwtUtils.extractClaims();

            Provenance provenance = buildProvenance(
                    resourceType, resourceId, tenantId, claims,
                    activityCode, activityDisplay);

            // Persist via the same persistence context
            FHIRPersistenceContext ctx = event.getPersistenceContext();
            if (ctx != null && ctx.getPersistence() != null) {
                ctx.getPersistence().create(ctx, provenance);
            } else {
                LOG.warning("ProvenanceGenerationInterceptor: no persistence context — Provenance not saved for "
                        + resourceType + "/" + resourceId);
            }

        } catch (Exception exc) {
            // Provenance failure must never block the primary operation
            LOG.log(Level.WARNING,
                    "ProvenanceGenerationInterceptor: failed to create Provenance resource",
                    exc);
        }
    }

    private Provenance buildProvenance(
            String resourceType,
            String resourceId,
            String tenantId,
            JsonObject claims,
            String activityCode,
            String activityDisplay) {

        String provenanceId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        // ---- Activity coding ----
        Coding activityCoding = Coding.builder()
                .system(Uri.of(DATA_OPERATION_SYSTEM))
                .code(Code.of(activityCode))
                .display(org.linuxforhealth.fhir.model.type.String.of(activityDisplay))
                .build();

        CodeableConcept activity = CodeableConcept.builder()
                .coding(activityCoding)
                .build();

        // ---- Target reference ----
        Reference target = Reference.builder()
                .reference(org.linuxforhealth.fhir.model.type.String.of(resourceType + "/" + resourceId))
                .build();

        // ---- Agent ----
        String agentRef = resolveAgentReference(claims);
        boolean isAiAgent = isAiAgentToken(claims);

        Coding agentTypeCoding = Coding.builder()
                .system(Uri.of(PARTICIPANT_TYPE_SYSTEM))
                .code(Code.of(isAiAgent ? "assembler" : "author"))
                .display(org.linuxforhealth.fhir.model.type.String.of(isAiAgent ? "Assembler" : "Author"))
                .build();

        CodeableConcept agentType = CodeableConcept.builder()
                .coding(agentTypeCoding)
                .build();

        Reference agentWho = Reference.builder()
                .reference(org.linuxforhealth.fhir.model.type.String.of(agentRef))
                .build();

        Provenance.Agent agent = Provenance.Agent.builder()
                .type(agentType)
                .who(agentWho)
                .build();

        // ---- Tenant extension ----
        Extension tenantExtension = Extension.builder()
                .url(TENANT_ID_EXTENSION_URL)
                .value(org.linuxforhealth.fhir.model.type.String.of(tenantId != null ? tenantId : "unknown"))
                .build();

        return Provenance.builder()
                .id(provenanceId)
                .meta(org.linuxforhealth.fhir.model.type.Meta.builder()
                        .profile(Canonical.of("https://clinivault.io/fhir/StructureDefinition/ClinivaultProvenance"))
                        .build())
                .recorded(now)
                .target(target)
                .activity(activity)
                .agent(agent)
                .extension(tenantExtension)
                .build();
    }

    // -----------------------------------------------------------------------
    // Helper: resolve the acting agent's FHIR reference string
    // -----------------------------------------------------------------------

    /**
     * Returns a FHIR reference string for the acting identity.
     * Priority: fhirUser claim > practitioner_id > agent_id > "Device/unknown-ai-agent"
     */
    private String resolveAgentReference(JsonObject claims) {
        if (claims == null) {
            return "Device/unknown-ai-agent";
        }

        // Human SMART user (fhirUser is already a qualified reference e.g. Practitioner/x)
        String fhirUser = getStringClaim(claims, CLAIM_FHIR_USER);
        if (fhirUser != null && !fhirUser.isEmpty()) {
            return fhirUser;
        }

        // Practitioner ID shorthand
        String practitionerId = getStringClaim(claims, CLAIM_PRACTITIONER_ID);
        if (practitionerId != null && !practitionerId.isEmpty()) {
            return "Practitioner/" + practitionerId;
        }

        // AI agent service account
        String agentId = getStringClaim(claims, CLAIM_AGENT_ID);
        if (agentId != null && !agentId.isEmpty()) {
            return "Device/" + agentId;
        }

        return "Device/unknown-ai-agent";
    }

    private boolean isAiAgentToken(JsonObject claims) {
        if (claims == null) return false;
        return claims.containsKey(CLAIM_AGENT_ID)
                && !claims.isNull(CLAIM_AGENT_ID)
                && !getStringClaim(claims, CLAIM_AGENT_ID).isEmpty();
    }

    private String getStringClaim(JsonObject claims, String name) {
        try {
            if (claims.containsKey(name) && !claims.isNull(name)) {
                return ((JsonString) claims.get(name)).getString();
            }
        } catch (ClassCastException ignore) {
        }
        return null;
    }

    private String resolveTenantId() {
        try {
            return FHIRRequestContext.get().getTenantId();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
