/*
 * (C) Copyright IBM Corp. 2024
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.linuxforhealth.fhir.core.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;

import org.testng.annotations.Test;

import org.linuxforhealth.fhir.core.FHIRVersionParam;

/**
 * Unit tests for {@link FHIRVersionParam}.
 */
public class FHIRVersionParamTest {

    // --- value() ---

    @Test
    public void testValueVersion40() {
        assertEquals(FHIRVersionParam.VERSION_40.value(), "4.0");
    }

    @Test
    public void testValueVersion43() {
        assertEquals(FHIRVersionParam.VERSION_43.value(), "4.3");
    }

    @Test
    public void testValueVersion50() {
        assertEquals(FHIRVersionParam.VERSION_50.value(), "5.0");
    }

    // --- from() — valid inputs ---

    @Test
    public void testFromVersion40() {
        assertEquals(FHIRVersionParam.from("4.0"), FHIRVersionParam.VERSION_40);
    }

    @Test
    public void testFromVersion43() {
        assertEquals(FHIRVersionParam.from("4.3"), FHIRVersionParam.VERSION_43);
    }

    @Test
    public void testFromVersion50() {
        assertEquals(FHIRVersionParam.from("5.0"), FHIRVersionParam.VERSION_50);
    }

    // --- from() — null ---

    @Test
    public void testFromNull() {
        assertNull(FHIRVersionParam.from(null));
    }

    // --- from() — invalid ---

    @Test
    public void testFromInvalidThrows() {
        assertThrows(IllegalArgumentException.class, () -> FHIRVersionParam.from("3.0"));
    }

    @Test
    public void testFromEmptyStringThrows() {
        assertThrows(IllegalArgumentException.class, () -> FHIRVersionParam.from(""));
    }

    @Test
    public void testFromPartialVersionThrows() {
        assertThrows(IllegalArgumentException.class, () -> FHIRVersionParam.from("4"));
    }

    // --- natural ordering (enum ordinal) ---

    @Test
    public void testOrderingVersion40BeforeVersion43() {
        // VERSION_40 is declared before VERSION_43 — ordinal must be smaller
        assert FHIRVersionParam.VERSION_40.compareTo(FHIRVersionParam.VERSION_43) < 0;
    }

    @Test
    public void testOrderingVersion43BeforeVersion50() {
        assert FHIRVersionParam.VERSION_43.compareTo(FHIRVersionParam.VERSION_50) < 0;
    }

    @Test
    public void testOrderingVersion40BeforeVersion50() {
        assert FHIRVersionParam.VERSION_40.compareTo(FHIRVersionParam.VERSION_50) < 0;
    }

    // --- round-trip: from(value()) ---

    @Test
    public void testRoundTripVersion40() {
        assertEquals(FHIRVersionParam.from(FHIRVersionParam.VERSION_40.value()), FHIRVersionParam.VERSION_40);
    }

    @Test
    public void testRoundTripVersion43() {
        assertEquals(FHIRVersionParam.from(FHIRVersionParam.VERSION_43.value()), FHIRVersionParam.VERSION_43);
    }

    @Test
    public void testRoundTripVersion50() {
        assertEquals(FHIRVersionParam.from(FHIRVersionParam.VERSION_50.value()), FHIRVersionParam.VERSION_50);
    }
}
