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
 * Validates that the datastore ID from the request context is in the set of
 * {@code allowed_dsids} registered for the current tenant.
 *
 * <p>This prevents a token issued for tenant A from routing to tenant B's
 * database via a crafted {@code X-FHIR-DSID} header.
 */
public class DSIDValidationInterceptor implements FHIRPersistenceInterceptor {

    private static final Logger LOG = Logger.getLogger(DSIDValidationInterceptor.class.getName());

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
        TenantRegistry registry = TenantRegistry.getInstance();
        if (registry.isEmpty()) {
            // Single-tenant fallback: skip DSID restriction
            return;
        }

        String tenantId = FHIRRequestContext.get().getTenantId();
        TenantRegistry.Entry entry = registry.findByTenantId(tenantId);
        if (entry == null) {
            throw new FHIRPersistenceInterceptorException(
                "DSIDValidationInterceptor: tenant '" + tenantId + "' not found in registry"
            );
        }

        String dsid = FHIRRequestContext.get().getDataStoreId();
        if (dsid == null || dsid.isEmpty()) {
            dsid = "default";
        }

        if (!entry.allowedDsids.contains(dsid)) {
            LOG.warning(
                "DSIDValidationInterceptor: dsid='" + dsid
                + "' is not in allowed_dsids=" + entry.allowedDsids
                + " for tenant='" + tenantId + "'"
            );
            throw new FHIRPersistenceInterceptorException(
                "DSIDValidationInterceptor: datastore '" + dsid
                + "' is not permitted for tenant '" + tenantId + "'"
            );
        }
    }
}
