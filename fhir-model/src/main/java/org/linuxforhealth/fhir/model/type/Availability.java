/*
 * (C) Copyright IBM Corp. 2019, 2022
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.linuxforhealth.fhir.model.type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.annotation.Generated;

import org.linuxforhealth.fhir.model.annotation.Binding;
import org.linuxforhealth.fhir.model.annotation.Constraint;
import org.linuxforhealth.fhir.model.annotation.Summary;
import org.linuxforhealth.fhir.model.type.code.BindingStrength;
import org.linuxforhealth.fhir.model.type.code.DaysOfWeek;
import org.linuxforhealth.fhir.model.util.ValidationSupport;
import org.linuxforhealth.fhir.model.visitor.Visitor;

/**
 * Availability data for an {item}.
 */
@Constraint(
    id = "av-1",
    level = "Rule",
    location = "Availability.availableTime",
    description = "Cannot include start/end times when selecting all day availability.",
    expression = "allDay.exists().not() or (allDay implies availableStartTime.exists().not() and availableEndTime.exists().not())",
    source = "http://hl7.org/fhir/StructureDefinition/Availability"
)
@Generated("org.linuxforhealth.fhir.tools.CodeGenerator")
public class Availability extends DataType {
    @Summary
    private final List<AvailableTime> availableTime;
    @Summary
    private final List<NotAvailableTime> notAvailableTime;

    private Availability(Builder builder) {
        super(builder);
        availableTime = Collections.unmodifiableList(builder.availableTime);
        notAvailableTime = Collections.unmodifiableList(builder.notAvailableTime);
    }

    /**
     * Times the {item} is available.
     * 
     * @return
     *     An unmodifiable list containing immutable objects of type {@link AvailableTime} that may be empty.
     */
    public List<AvailableTime> getAvailableTime() {
        return availableTime;
    }

    /**
     * Not available during this time due to provided reason.
     * 
     * @return
     *     An unmodifiable list containing immutable objects of type {@link NotAvailableTime} that may be empty.
     */
    public List<NotAvailableTime> getNotAvailableTime() {
        return notAvailableTime;
    }

    @Override
    public boolean hasChildren() {
        return super.hasChildren() || 
            !availableTime.isEmpty() || 
            !notAvailableTime.isEmpty();
    }

    @Override
    public void accept(java.lang.String elementName, int elementIndex, Visitor visitor) {
        if (visitor.preVisit(this)) {
            visitor.visitStart(elementName, elementIndex, this);
            if (visitor.visit(elementName, elementIndex, this)) {
                // visit children
                accept(id, "id", visitor);
                accept(extension, "extension", visitor, Extension.class);
                accept(availableTime, "availableTime", visitor, AvailableTime.class);
                accept(notAvailableTime, "notAvailableTime", visitor, NotAvailableTime.class);
            }
            visitor.visitEnd(elementName, elementIndex, this);
            visitor.postVisit(this);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Availability other = (Availability) obj;
        return Objects.equals(id, other.id) && 
            Objects.equals(extension, other.extension) && 
            Objects.equals(availableTime, other.availableTime) && 
            Objects.equals(notAvailableTime, other.notAvailableTime);
    }

    @Override
    public int hashCode() {
        int result = hashCode;
        if (result == 0) {
            result = Objects.hash(id, 
                extension, 
                availableTime, 
                notAvailableTime);
            hashCode = result;
        }
        return result;
    }

    @Override
    public Builder toBuilder() {
        return new Builder().from(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends DataType.Builder {
        private List<AvailableTime> availableTime = new ArrayList<>();
        private List<NotAvailableTime> notAvailableTime = new ArrayList<>();

        private Builder() {
            super();
        }

        /**
         * Unique id for the element within a resource (for internal references). This may be any string value that does not 
         * contain spaces.
         * 
         * @param id
         *     Unique id for inter-element referencing
         * 
         * @return
         *     A reference to this Builder instance
         */
        @Override
        public Builder id(java.lang.String id) {
            return (Builder) super.id(id);
        }

        /**
         * May be used to represent additional information that is not part of the basic definition of the element. To make the 
         * use of extensions safe and managable, there is a strict set of governance applied to the definition and use of 
         * extensions. Though any implementer can define an extension, there is a set of requirements that SHALL be met as part 
         * of the definition of the extension.
         * 
         * <p>Adds new element(s) to the existing list.
         * If any of the elements are null, calling {@link #build()} will fail.
         * 
         * @param extension
         *     Additional content defined by implementations
         * 
         * @return
         *     A reference to this Builder instance
         */
        @Override
        public Builder extension(Extension... extension) {
            return (Builder) super.extension(extension);
        }

        /**
         * May be used to represent additional information that is not part of the basic definition of the element. To make the 
         * use of extensions safe and managable, there is a strict set of governance applied to the definition and use of 
         * extensions. Though any implementer can define an extension, there is a set of requirements that SHALL be met as part 
         * of the definition of the extension.
         * 
         * <p>Replaces the existing list with a new one containing elements from the Collection.
         * If any of the elements are null, calling {@link #build()} will fail.
         * 
         * @param extension
         *     Additional content defined by implementations
         * 
         * @return
         *     A reference to this Builder instance
         * 
         * @throws NullPointerException
         *     If the passed collection is null
         */
        @Override
        public Builder extension(Collection<Extension> extension) {
            return (Builder) super.extension(extension);
        }

        /**
         * Times the {item} is available.
         * 
         * <p>Adds new element(s) to the existing list.
         * If any of the elements are null, calling {@link #build()} will fail.
         * 
         * @param availableTime
         *     Times the {item} is available
         * 
         * @return
         *     A reference to this Builder instance
         */
        public Builder availableTime(AvailableTime... availableTime) {
            for (AvailableTime value : availableTime) {
                this.availableTime.add(value);
            }
            return this;
        }

        /**
         * Times the {item} is available.
         * 
         * <p>Replaces the existing list with a new one containing elements from the Collection.
         * If any of the elements are null, calling {@link #build()} will fail.
         * 
         * @param availableTime
         *     Times the {item} is available
         * 
         * @return
         *     A reference to this Builder instance
         * 
         * @throws NullPointerException
         *     If the passed collection is null
         */
        public Builder availableTime(Collection<AvailableTime> availableTime) {
            this.availableTime = new ArrayList<>(availableTime);
            return this;
        }

        /**
         * Not available during this time due to provided reason.
         * 
         * <p>Adds new element(s) to the existing list.
         * If any of the elements are null, calling {@link #build()} will fail.
         * 
         * @param notAvailableTime
         *     Not available during this time due to provided reason
         * 
         * @return
         *     A reference to this Builder instance
         */
        public Builder notAvailableTime(NotAvailableTime... notAvailableTime) {
            for (NotAvailableTime value : notAvailableTime) {
                this.notAvailableTime.add(value);
            }
            return this;
        }

        /**
         * Not available during this time due to provided reason.
         * 
         * <p>Replaces the existing list with a new one containing elements from the Collection.
         * If any of the elements are null, calling {@link #build()} will fail.
         * 
         * @param notAvailableTime
         *     Not available during this time due to provided reason
         * 
         * @return
         *     A reference to this Builder instance
         * 
         * @throws NullPointerException
         *     If the passed collection is null
         */
        public Builder notAvailableTime(Collection<NotAvailableTime> notAvailableTime) {
            this.notAvailableTime = new ArrayList<>(notAvailableTime);
            return this;
        }

        /**
         * Build the {@link Availability}
         * 
         * @return
         *     An immutable object of type {@link Availability}
         * @throws IllegalStateException
         *     if the current state cannot be built into a valid Availability per the base specification
         */
        @Override
        public Availability build() {
            Availability availability = new Availability(this);
            if (validating) {
                validate(availability);
            }
            return availability;
        }

        protected void validate(Availability availability) {
            super.validate(availability);
            ValidationSupport.checkList(availability.availableTime, "availableTime", AvailableTime.class);
            ValidationSupport.checkList(availability.notAvailableTime, "notAvailableTime", NotAvailableTime.class);
            ValidationSupport.requireValueOrChildren(availability);
        }

        protected Builder from(Availability availability) {
            super.from(availability);
            availableTime.addAll(availability.availableTime);
            notAvailableTime.addAll(availability.notAvailableTime);
            return this;
        }
    }

    /**
     * Times the {item} is available.
     */
    public static class AvailableTime extends BackboneElement {
        @Summary
        @Binding(
            bindingName = "DaysOfWeek",
            strength = BindingStrength.Value.REQUIRED,
            description = "The purpose for which an extended contact detail should be used.",
            valueSet = "http://hl7.org/fhir/ValueSet/days-of-week|5.0.0"
        )
        private final List<DaysOfWeek> daysOfWeek;
        @Summary
        private final Boolean allDay;
        @Summary
        private final Time availableStartTime;
        @Summary
        private final Time availableEndTime;

        private AvailableTime(Builder builder) {
            super(builder);
            daysOfWeek = Collections.unmodifiableList(builder.daysOfWeek);
            allDay = builder.allDay;
            availableStartTime = builder.availableStartTime;
            availableEndTime = builder.availableEndTime;
        }

        /**
         * mon | tue | wed | thu | fri | sat | sun.
         * 
         * @return
         *     An unmodifiable list containing immutable objects of type {@link DaysOfWeek} that may be empty.
         */
        public List<DaysOfWeek> getDaysOfWeek() {
            return daysOfWeek;
        }

        /**
         * Always available? i.e. 24 hour service.
         * 
         * @return
         *     An immutable object of type {@link Boolean} that may be null.
         */
        public Boolean getAllDay() {
            return allDay;
        }

        /**
         * Opening time of day (ignored if allDay = true).
         * 
         * @return
         *     An immutable object of type {@link Time} that may be null.
         */
        public Time getAvailableStartTime() {
            return availableStartTime;
        }

        /**
         * Closing time of day (ignored if allDay = true).
         * 
         * @return
         *     An immutable object of type {@link Time} that may be null.
         */
        public Time getAvailableEndTime() {
            return availableEndTime;
        }

        @Override
        public boolean hasChildren() {
            return super.hasChildren() || 
                !daysOfWeek.isEmpty() || 
                (allDay != null) || 
                (availableStartTime != null) || 
                (availableEndTime != null);
        }

        @Override
        public void accept(java.lang.String elementName, int elementIndex, Visitor visitor) {
            if (visitor.preVisit(this)) {
                visitor.visitStart(elementName, elementIndex, this);
                if (visitor.visit(elementName, elementIndex, this)) {
                    // visit children
                    accept(id, "id", visitor);
                    accept(extension, "extension", visitor, Extension.class);
                    accept(daysOfWeek, "daysOfWeek", visitor, DaysOfWeek.class);
                    accept(allDay, "allDay", visitor);
                    accept(availableStartTime, "availableStartTime", visitor);
                    accept(availableEndTime, "availableEndTime", visitor);
                }
                visitor.visitEnd(elementName, elementIndex, this);
                visitor.postVisit(this);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            AvailableTime other = (AvailableTime) obj;
            return Objects.equals(id, other.id) && 
                Objects.equals(extension, other.extension) && 
                Objects.equals(daysOfWeek, other.daysOfWeek) && 
                Objects.equals(allDay, other.allDay) && 
                Objects.equals(availableStartTime, other.availableStartTime) && 
                Objects.equals(availableEndTime, other.availableEndTime);
        }

        @Override
        public int hashCode() {
            int result = hashCode;
            if (result == 0) {
                result = Objects.hash(id, 
                    extension, 
                    daysOfWeek, 
                    allDay, 
                    availableStartTime, 
                    availableEndTime);
                hashCode = result;
            }
            return result;
        }

        @Override
        public Builder toBuilder() {
            return new Builder().from(this);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder extends BackboneElement.Builder {
            private List<DaysOfWeek> daysOfWeek = new ArrayList<>();
            private Boolean allDay;
            private Time availableStartTime;
            private Time availableEndTime;

            private Builder() {
                super();
            }

            /**
             * Unique id for the element within a resource (for internal references). This may be any string value that does not 
             * contain spaces.
             * 
             * @param id
             *     Unique id for inter-element referencing
             * 
             * @return
             *     A reference to this Builder instance
             */
            @Override
            public Builder id(java.lang.String id) {
                return (Builder) super.id(id);
            }

            /**
             * May be used to represent additional information that is not part of the basic definition of the element. To make the 
             * use of extensions safe and managable, there is a strict set of governance applied to the definition and use of 
             * extensions. Though any implementer can define an extension, there is a set of requirements that SHALL be met as part 
             * of the definition of the extension.
             * 
             * <p>Adds new element(s) to the existing list.
             * If any of the elements are null, calling {@link #build()} will fail.
             * 
             * @param extension
             *     Additional content defined by implementations
             * 
             * @return
             *     A reference to this Builder instance
             */
            @Override
            public Builder extension(Extension... extension) {
                return (Builder) super.extension(extension);
            }

            /**
             * May be used to represent additional information that is not part of the basic definition of the element. To make the 
             * use of extensions safe and managable, there is a strict set of governance applied to the definition and use of 
             * extensions. Though any implementer can define an extension, there is a set of requirements that SHALL be met as part 
             * of the definition of the extension.
             * 
             * <p>Replaces the existing list with a new one containing elements from the Collection.
             * If any of the elements are null, calling {@link #build()} will fail.
             * 
             * @param extension
             *     Additional content defined by implementations
             * 
             * @return
             *     A reference to this Builder instance
             * 
             * @throws NullPointerException
             *     If the passed collection is null
             */
            @Override
            public Builder extension(Collection<Extension> extension) {
                return (Builder) super.extension(extension);
            }

            /**
             * mon | tue | wed | thu | fri | sat | sun.
             * 
             * <p>Adds new element(s) to the existing list.
             * If any of the elements are null, calling {@link #build()} will fail.
             * 
             * @param daysOfWeek
             *     mon | tue | wed | thu | fri | sat | sun
             * 
             * @return
             *     A reference to this Builder instance
             */
            public Builder daysOfWeek(DaysOfWeek... daysOfWeek) {
                for (DaysOfWeek value : daysOfWeek) {
                    this.daysOfWeek.add(value);
                }
                return this;
            }

            /**
             * mon | tue | wed | thu | fri | sat | sun.
             * 
             * <p>Replaces the existing list with a new one containing elements from the Collection.
             * If any of the elements are null, calling {@link #build()} will fail.
             * 
             * @param daysOfWeek
             *     mon | tue | wed | thu | fri | sat | sun
             * 
             * @return
             *     A reference to this Builder instance
             * 
             * @throws NullPointerException
             *     If the passed collection is null
             */
            public Builder daysOfWeek(Collection<DaysOfWeek> daysOfWeek) {
                this.daysOfWeek = new ArrayList<>(daysOfWeek);
                return this;
            }

            /**
             * Convenience method for setting {@code allDay}.
             * 
             * @param allDay
             *     Always available? i.e. 24 hour service
             * 
             * @return
             *     A reference to this Builder instance
             * 
             * @see #allDay(org.linuxforhealth.fhir.model.type.Boolean)
             */
            public Builder allDay(java.lang.Boolean allDay) {
                this.allDay = (allDay == null) ? null : Boolean.of(allDay);
                return this;
            }

            /**
             * Always available? i.e. 24 hour service.
             * 
             * @param allDay
             *     Always available? i.e. 24 hour service
             * 
             * @return
             *     A reference to this Builder instance
             */
            public Builder allDay(Boolean allDay) {
                this.allDay = allDay;
                return this;
            }

            /**
             * Convenience method for setting {@code availableStartTime}.
             * 
             * @param availableStartTime
             *     Opening time of day (ignored if allDay = true)
             * 
             * @return
             *     A reference to this Builder instance
             * 
             * @see #availableStartTime(org.linuxforhealth.fhir.model.type.Time)
             */
            public Builder availableStartTime(java.time.LocalTime availableStartTime) {
                this.availableStartTime = (availableStartTime == null) ? null : Time.of(availableStartTime);
                return this;
            }

            /**
             * Opening time of day (ignored if allDay = true).
             * 
             * @param availableStartTime
             *     Opening time of day (ignored if allDay = true)
             * 
             * @return
             *     A reference to this Builder instance
             */
            public Builder availableStartTime(Time availableStartTime) {
                this.availableStartTime = availableStartTime;
                return this;
            }

            /**
             * Convenience method for setting {@code availableEndTime}.
             * 
             * @param availableEndTime
             *     Closing time of day (ignored if allDay = true)
             * 
             * @return
             *     A reference to this Builder instance
             * 
             * @see #availableEndTime(org.linuxforhealth.fhir.model.type.Time)
             */
            public Builder availableEndTime(java.time.LocalTime availableEndTime) {
                this.availableEndTime = (availableEndTime == null) ? null : Time.of(availableEndTime);
                return this;
            }

            /**
             * Closing time of day (ignored if allDay = true).
             * 
             * @param availableEndTime
             *     Closing time of day (ignored if allDay = true)
             * 
             * @return
             *     A reference to this Builder instance
             */
            public Builder availableEndTime(Time availableEndTime) {
                this.availableEndTime = availableEndTime;
                return this;
            }

            /**
             * Build the {@link AvailableTime}
             * 
             * @return
             *     An immutable object of type {@link AvailableTime}
             * @throws IllegalStateException
             *     if the current state cannot be built into a valid AvailableTime per the base specification
             */
            @Override
            public AvailableTime build() {
                AvailableTime availableTime = new AvailableTime(this);
                if (validating) {
                    validate(availableTime);
                }
                return availableTime;
            }

            protected void validate(AvailableTime availableTime) {
                super.validate(availableTime);
                ValidationSupport.checkList(availableTime.daysOfWeek, "daysOfWeek", DaysOfWeek.class);
                ValidationSupport.requireValueOrChildren(availableTime);
            }

            protected Builder from(AvailableTime availableTime) {
                super.from(availableTime);
                daysOfWeek.addAll(availableTime.daysOfWeek);
                allDay = availableTime.allDay;
                availableStartTime = availableTime.availableStartTime;
                availableEndTime = availableTime.availableEndTime;
                return this;
            }
        }
    }

    /**
     * Not available during this time due to provided reason.
     */
    public static class NotAvailableTime extends BackboneElement {
        @Summary
        private final String description;
        @Summary
        private final Period during;

        private NotAvailableTime(Builder builder) {
            super(builder);
            description = builder.description;
            during = builder.during;
        }

        /**
         * Reason presented to the user explaining why time not available.
         * 
         * @return
         *     An immutable object of type {@link String} that may be null.
         */
        public String getDescription() {
            return description;
        }

        /**
         * Service not available during this period.
         * 
         * @return
         *     An immutable object of type {@link Period} that may be null.
         */
        public Period getDuring() {
            return during;
        }

        @Override
        public boolean hasChildren() {
            return super.hasChildren() || 
                (description != null) || 
                (during != null);
        }

        @Override
        public void accept(java.lang.String elementName, int elementIndex, Visitor visitor) {
            if (visitor.preVisit(this)) {
                visitor.visitStart(elementName, elementIndex, this);
                if (visitor.visit(elementName, elementIndex, this)) {
                    // visit children
                    accept(id, "id", visitor);
                    accept(extension, "extension", visitor, Extension.class);
                    accept(description, "description", visitor);
                    accept(during, "during", visitor);
                }
                visitor.visitEnd(elementName, elementIndex, this);
                visitor.postVisit(this);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            NotAvailableTime other = (NotAvailableTime) obj;
            return Objects.equals(id, other.id) && 
                Objects.equals(extension, other.extension) && 
                Objects.equals(description, other.description) && 
                Objects.equals(during, other.during);
        }

        @Override
        public int hashCode() {
            int result = hashCode;
            if (result == 0) {
                result = Objects.hash(id, 
                    extension, 
                    description, 
                    during);
                hashCode = result;
            }
            return result;
        }

        @Override
        public Builder toBuilder() {
            return new Builder().from(this);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder extends BackboneElement.Builder {
            private String description;
            private Period during;

            private Builder() {
                super();
            }

            /**
             * Unique id for the element within a resource (for internal references). This may be any string value that does not 
             * contain spaces.
             * 
             * @param id
             *     Unique id for inter-element referencing
             * 
             * @return
             *     A reference to this Builder instance
             */
            @Override
            public Builder id(java.lang.String id) {
                return (Builder) super.id(id);
            }

            /**
             * May be used to represent additional information that is not part of the basic definition of the element. To make the 
             * use of extensions safe and managable, there is a strict set of governance applied to the definition and use of 
             * extensions. Though any implementer can define an extension, there is a set of requirements that SHALL be met as part 
             * of the definition of the extension.
             * 
             * <p>Adds new element(s) to the existing list.
             * If any of the elements are null, calling {@link #build()} will fail.
             * 
             * @param extension
             *     Additional content defined by implementations
             * 
             * @return
             *     A reference to this Builder instance
             */
            @Override
            public Builder extension(Extension... extension) {
                return (Builder) super.extension(extension);
            }

            /**
             * May be used to represent additional information that is not part of the basic definition of the element. To make the 
             * use of extensions safe and managable, there is a strict set of governance applied to the definition and use of 
             * extensions. Though any implementer can define an extension, there is a set of requirements that SHALL be met as part 
             * of the definition of the extension.
             * 
             * <p>Replaces the existing list with a new one containing elements from the Collection.
             * If any of the elements are null, calling {@link #build()} will fail.
             * 
             * @param extension
             *     Additional content defined by implementations
             * 
             * @return
             *     A reference to this Builder instance
             * 
             * @throws NullPointerException
             *     If the passed collection is null
             */
            @Override
            public Builder extension(Collection<Extension> extension) {
                return (Builder) super.extension(extension);
            }

            /**
             * Convenience method for setting {@code description}.
             * 
             * @param description
             *     Reason presented to the user explaining why time not available
             * 
             * @return
             *     A reference to this Builder instance
             * 
             * @see #description(org.linuxforhealth.fhir.model.type.String)
             */
            public Builder description(java.lang.String description) {
                this.description = (description == null) ? null : String.of(description);
                return this;
            }

            /**
             * Reason presented to the user explaining why time not available.
             * 
             * @param description
             *     Reason presented to the user explaining why time not available
             * 
             * @return
             *     A reference to this Builder instance
             */
            public Builder description(String description) {
                this.description = description;
                return this;
            }

            /**
             * Service not available during this period.
             * 
             * @param during
             *     Service not available during this period
             * 
             * @return
             *     A reference to this Builder instance
             */
            public Builder during(Period during) {
                this.during = during;
                return this;
            }

            /**
             * Build the {@link NotAvailableTime}
             * 
             * @return
             *     An immutable object of type {@link NotAvailableTime}
             * @throws IllegalStateException
             *     if the current state cannot be built into a valid NotAvailableTime per the base specification
             */
            @Override
            public NotAvailableTime build() {
                NotAvailableTime notAvailableTime = new NotAvailableTime(this);
                if (validating) {
                    validate(notAvailableTime);
                }
                return notAvailableTime;
            }

            protected void validate(NotAvailableTime notAvailableTime) {
                super.validate(notAvailableTime);
                ValidationSupport.requireValueOrChildren(notAvailableTime);
            }

            protected Builder from(NotAvailableTime notAvailableTime) {
                super.from(notAvailableTime);
                description = notAvailableTime.description;
                during = notAvailableTime.during;
                return this;
            }
        }
    }
}
