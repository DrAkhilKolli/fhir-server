/*
 * (C) Copyright Clinivault Inc. 2024, 2026
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.clinivault.fhir.interceptors;

import java.time.Instant;
import java.util.logging.Logger;

import org.linuxforhealth.fhir.config.FHIRRequestContext;
import org.linuxforhealth.fhir.model.resource.Resource;
import org.linuxforhealth.fhir.persistence.context.FHIRPersistenceEvent;
import org.linuxforhealth.fhir.server.spi.interceptor.FHIRPersistenceInterceptor;
import org.linuxforhealth.fhir.server.spi.interceptor.FHIRPersistenceInterceptorException;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

/**
 * Emits a structured JSON audit log entry for every FHIR read and write
 * operation that passes the upstream validation interceptors.
 *
 * <p>Log records are written at INFO level to the JUL logger
 * {@code org.clinivault.fhir.interceptors.AuditPersistenceInterceptor}.
 * Configure that logger's handler(s) to route records to a dedicated audit
 * appender / CloudWatch log group.
 *
 * <p>Example record:
 * <pre>
 * {
 *   "ts":        "2024-06-15T12:00:00Z",
 *   "event":     "READ",
 *   "tenant":    "acme-001",
 *   "dsid":      "default",
 *   "sub":       "user-uuid",
 *   "resourceType": "Patient",
 *   "resourceId": "pat-123"
 * }
 * </pre>
 */
public class AuditPersistenceInterceptor implements FHIRPersistenceInterceptor {

    private static final Logger AUDIT = Logger.getLogger(
        AuditPersistenceInterceptor.class.getName()
    );

    // ---- after-* hooks (post-operation, resource is available) ---------------

    @Override
    public void afterRead(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        audit("READ", event);
    }

    @Override
    public void afterVread(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        audit("VREAD", event);
    }

    @Override
    public void afterHistory(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        audit("HISTORY", event);
    }

    @Override
    public void afterSearch(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        audit("SEARCH", event);
    }

    @Override
    public void afterCreate(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        audit("CREATE", event);
    }

    @Override
    public void afterUpdate(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        audit("UPDATE", event);
    }

    @Override
    public void afterDelete(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        audit("DELETE", event);
    }

    // ---- helpers -------------------------------------------------------------

    private void audit(String eventType, FHIRPersistenceEvent event) {
        try {
            FHIRRequestContext ctx = FHIRRequestContext.get();
            String tenantId = ctx.getTenantId();
            String dsid     = ctx.getDataStoreId();
            if (dsid == null || dsid.isEmpty()) dsid = "default";

            // Extract subject from JWT claims (best-effort, no throw)
            String sub = "";
            JsonObject claims = JwtUtils.extractClaims();
            if (claims != null) {
                sub = claims.getString("sub", "");
            }

            // Resource type + id (best-effort)
            String resourceType = "";
            String resourceId   = "";
            Object fhirResource = event.getFhirResource();
            if (fhirResource instanceof Resource) {
                Resource r = (Resource) fhirResource;
                resourceType = r.getClass().getSimpleName();
                if (r.getId() != null) {
                    resourceId = r.getId();
                }
            }

            JsonObjectBuilder builder = Json.createObjectBuilder()
                .add("ts",           Instant.now().toString())
                .add("event",        eventType)
                .add("tenant",       tenantId != null ? tenantId : "")
                .add("dsid",         dsid)
                .add("sub",          sub)
                .add("resourceType", resourceType)
                .add("resourceId",   resourceId);

            AUDIT.info(builder.build().toString());
        } catch (Exception e) {
            // Never let audit logging break the operation
            AUDIT.warning("AuditPersistenceInterceptor: failed to emit record: " + e.getMessage());
        }
    }
}
