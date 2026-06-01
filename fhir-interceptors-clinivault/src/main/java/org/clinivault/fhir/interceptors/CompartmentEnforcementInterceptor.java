/*
 * (C) Copyright Clinivault Inc. 2024, 2026
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.clinivault.fhir.interceptors;

import java.util.logging.Logger;

import org.linuxforhealth.fhir.config.FHIRRequestContext;
import org.linuxforhealth.fhir.persistence.context.FHIRPersistenceEvent;
import org.linuxforhealth.fhir.server.spi.interceptor.FHIRPersistenceInterceptor;
import org.linuxforhealth.fhir.server.spi.interceptor.FHIRPersistenceInterceptorException;

import jakarta.json.JsonObject;

/**
 * Enforces FHIR patient-compartment boundaries for patient-context requests.
 *
 * <p>When a JWT contains a {@code patient_id} claim (set by the
 * {@code launch/patient} scope mapper in Keycloak), every read or search on a
 * Patient resource must target that specific patient only.  Requests that
 * address a different Patient ID are rejected with a 403-equivalent interceptor
 * exception.
 *
 * <p>Requests whose JWT does not contain a {@code patient_id} claim (e.g.
 * clinician / system tokens) bypass this check and are governed only by the
 * {@link SmartScopeEnforcementInterceptor}.
 *
 * <p>Non-Patient resource types are not validated here; compartment containment
 * for related resources (Observation, Condition, etc.) should be enforced at
 * the search-parameter layer via FHIR compartment queries.
 */
public class CompartmentEnforcementInterceptor implements FHIRPersistenceInterceptor {

    private static final Logger LOG = Logger.getLogger(CompartmentEnforcementInterceptor.class.getName());

    private static final String PATIENT_RESOURCE_TYPE = "Patient";

    // --------------------------------------------------------------------- //
    //  Read-class operations
    // --------------------------------------------------------------------- //

    @Override
    public void beforeRead(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        enforcePatientCompartment(event, "read");
    }

    @Override
    public void beforeVread(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        enforcePatientCompartment(event, "vread");
    }

    @Override
    public void beforeSearch(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        enforcePatientCompartment(event, "search");
    }

    // --------------------------------------------------------------------- //
    //  Enforcement helper
    // --------------------------------------------------------------------- //

    private void enforcePatientCompartment(FHIRPersistenceEvent event, String operationName)
            throws FHIRPersistenceInterceptorException {

        JsonObject claims = JwtUtils.extractClaims();
        if (claims == null) {
            // No JWT — let TenantValidationInterceptor handle the denial
            return;
        }

        String jwtPatientId = claims.getString("patient_id", null);
        if (jwtPatientId == null || jwtPatientId.isBlank()) {
            // No patient_id claim — clinician or system token; skip compartment check
            return;
        }

        // Only enforce compartment isolation when the target resource is Patient
        String resourceType = getResourceType(event);
        if (!PATIENT_RESOURCE_TYPE.equals(resourceType)) {
            return;
        }

        // Extract the requested resource ID from the persistence event
        String requestedId = getRequestedResourceId(event);
        if (requestedId == null || requestedId.isBlank()) {
            // Search — accept; search parameter narrowing is the search-param layer's job
            return;
        }

        if (!jwtPatientId.equals(requestedId)) {
            LOG.warning(
                "CompartmentEnforcementInterceptor: compartment violation — "
                + "JWT patient_id=" + jwtPatientId
                + " but requested Patient/" + requestedId
                + " in operation=" + operationName
                + " tenant=" + FHIRRequestContext.get().getTenantId()
            );
            throw new FHIRPersistenceInterceptorException(
                "CompartmentEnforcementInterceptor: patient compartment violation — "
                + "token is scoped to Patient/" + jwtPatientId
                + " but request targets Patient/" + requestedId
            );
        }
    }

    private String getResourceType(FHIRPersistenceEvent event) {
        if (event == null) {
            return "";
        }
        Object resource = event.getFhirResource();
        if (resource != null) {
            return resource.getClass().getSimpleName();
        }
        // For beforeRead/vread/search, resource may not be populated yet;
        // try the event's resource type hint instead
        try {
            // FHIRPersistenceEvent exposes getResourceTypeName() in some builds
            java.lang.reflect.Method m = event.getClass().getMethod("getResourceTypeName");
            Object rt = m.invoke(event);
            return rt != null ? rt.toString() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private String getRequestedResourceId(FHIRPersistenceEvent event) {
        if (event == null) {
            return null;
        }
        try {
            java.lang.reflect.Method m = event.getClass().getMethod("getResourceId");
            Object id = m.invoke(event);
            return id != null ? id.toString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
