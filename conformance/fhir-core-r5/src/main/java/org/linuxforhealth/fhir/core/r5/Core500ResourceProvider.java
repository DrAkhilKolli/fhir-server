/*
 * (C) Copyright IBM Corp. 2024
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.linuxforhealth.fhir.core.r5;

import org.linuxforhealth.fhir.registry.util.PackageRegistryResourceProvider;

public class Core500ResourceProvider extends PackageRegistryResourceProvider {
    @Override
    public String getPackageId() {
        return "hl7.fhir.core.500";
    }
}
