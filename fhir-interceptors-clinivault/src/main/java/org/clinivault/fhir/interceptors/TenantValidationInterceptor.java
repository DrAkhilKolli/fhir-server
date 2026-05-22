/*
 * (C) Copyright Clinivault Inc. 2024, 2026
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.clinivault.fhir.interceptors;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.linuxforhealth.fhir.config.FHIRRequestContext;
import org.linuxforhealth.fhir.persistence.context.FHIRPersistenceEvent;
import org.linuxforhealth.fhir.server.spi.interceptor.FHIRPersistenceInterceptor;
import org.linuxforhealth.fhir.server.spi.interceptor.FHIRPersistenceInterceptorException;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import java.io.StringReader;

/**
 * Validates that the JWT {@code tenant_id} claim matches the Liberty tenant
 * derived from the {@code X-FHIR-TENANT-ID} request header.
 *
 * <p>Executed first in the interceptor chain so that requests with an
 * inconsistent tenant identity are rejected before any persistence work.
 */
public class TenantValidationInterceptor implements FHIRPersistenceInterceptor {

    private static final Logger LOG = Logger.getLogger(TenantValidationInterceptor.class.getName());

    @Override
    public void beforeRead(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        validate(event);
    }

    @Override
    public void beforeVread(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        validate(event);
    }

    @Override
    public void beforeHistory(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        validate(event);
    }

    @Override
    public void beforeSearch(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        validate(event);
    }

    @Override
    public void beforeCreate(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        validate(event);
    }

    @Override
    public void beforeUpdate(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        validate(event);
    }

    @Override
    public void beforeDelete(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        validate(event);
    }

    // -------------------------------------------------------------------------

    private void validate(FHIRPersistenceEvent event) throws FHIRPersistenceInterceptorException {
        String libertyTenantId = FHIRRequestContext.get().getTenantId();

        JsonObject claims = JwtUtils.extractClaims();
        if (claims == null) {
            // No Authorization header (e.g. metadata endpoint — should not reach interceptors,
            // but fail closed if it does).
            throw new FHIRPersistenceInterceptorException(
                "TenantValidationInterceptor: missing JWT — no Authorization header"
            );
        }

        String jwtTenantId = claims.getString("tenant_id", null);
        if (jwtTenantId == null || jwtTenantId.isEmpty()) {
            throw new FHIRPersistenceInterceptorException(
                "TenantValidationInterceptor: JWT is missing required claim 'tenant_id'"
            );
        }

        if (!jwtTenantId.equals(libertyTenantId)) {
            LOG.warning(
                "TenantValidationInterceptor: JWT tenant_id=" + jwtTenantId
                + " does not match Liberty tenant=" + libertyTenantId
            );
            throw new FHIRPersistenceInterceptorException(
                "TenantValidationInterceptor: JWT tenant_id does not match request tenant"
            );
        }
    }
}
