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
 * Validates that the JWT {@code iss} claim's realm segment (last URL path
 * component) matches the registered realm for the current tenant.
 *
 * <p>Relies on {@link TenantRegistry} which is populated from the tenant
 * registry JSON downloaded by {@code start.sh}.
 */
public class RealmValidationInterceptor implements FHIRPersistenceInterceptor {

    private static final Logger LOG = Logger.getLogger(RealmValidationInterceptor.class.getName());

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
        String tenantId = FHIRRequestContext.get().getTenantId();

        TenantRegistry registry = TenantRegistry.getInstance();
        if (registry.isEmpty()) {
            // Single-tenant fallback: skip cross-registry check
            return;
        }

        TenantRegistry.Entry entry = registry.findByTenantId(tenantId);
        if (entry == null) {
            throw new FHIRPersistenceInterceptorException(
                "RealmValidationInterceptor: tenant '" + tenantId + "' not found in registry"
            );
        }

        JsonObject claims = JwtUtils.extractClaims();
        if (claims == null) {
            throw new FHIRPersistenceInterceptorException(
                "RealmValidationInterceptor: missing JWT"
            );
        }

        String issuer = claims.getString("iss", null);
        if (issuer == null || issuer.isEmpty()) {
            throw new FHIRPersistenceInterceptorException(
                "RealmValidationInterceptor: JWT is missing 'iss' claim"
            );
        }

        // Derive realm from issuer URL last segment
        String issuerRealm = deriveRealm(issuer);
        if (!entry.realm.equals(issuerRealm)) {
            LOG.warning(
                "RealmValidationInterceptor: issuer realm='" + issuerRealm
                + "' does not match registered realm='" + entry.realm
                + "' for tenant='" + tenantId + "'"
            );
            throw new FHIRPersistenceInterceptorException(
                "RealmValidationInterceptor: JWT issuer realm does not match tenant registration"
            );
        }
    }

    /** Extracts the last non-empty path segment from a URL. */
    static String deriveRealm(String url) {
        if (url == null) return "";
        String stripped = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        int idx = stripped.lastIndexOf('/');
        return idx >= 0 ? stripped.substring(idx + 1) : stripped;
    }
}
