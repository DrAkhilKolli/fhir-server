/*
 * (C) Copyright IBM Corp. 2019, 2022
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.linuxforhealth.fhir.model.type;

import javax.annotation.Generated;

import org.linuxforhealth.fhir.model.builder.AbstractBuilder;
import org.linuxforhealth.fhir.model.visitor.AbstractVisitable;

/**
 * Base definition for all types defined in FHIR type system.
 */
@Generated("org.linuxforhealth.fhir.tools.CodeGenerator")
public abstract class Base extends AbstractVisitable {
    protected Base(Builder builder) {
    }

    public boolean hasChildren() {
        return false;
    }

    /**
     * Create a new Builder from the contents of this Base
     */
    public abstract Builder toBuilder();

    public static abstract class Builder extends AbstractBuilder<Base> {
        protected Builder() {
            super();
        }

        @Override
        public abstract Base build();

        protected void validate(Base base) {
        }

        protected Builder from(Base base) {
            return this;
        }
    }
}
