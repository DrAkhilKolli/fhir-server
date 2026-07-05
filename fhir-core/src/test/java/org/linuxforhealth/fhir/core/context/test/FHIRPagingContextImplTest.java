/*
 * (C) Copyright IBM Corp. 2024
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.linuxforhealth.fhir.core.context.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.linuxforhealth.fhir.core.FHIRConstants;
import org.linuxforhealth.fhir.core.context.impl.FHIRPagingContextImpl;

/**
 * Unit tests for {@link FHIRPagingContextImpl}.
 */
public class FHIRPagingContextImplTest {

    private FHIRPagingContextImpl ctx;

    @BeforeMethod
    public void setUp() {
        ctx = new FHIRPagingContextImpl();
    }

    // --- default values ---

    @Test
    public void testDefaultPageNumber() {
        assertEquals(ctx.getPageNumber(), FHIRConstants.FHIR_PAGE_NUMBER_DEFAULT);
    }

    @Test
    public void testDefaultPageSize() {
        assertEquals(ctx.getPageSize(), FHIRConstants.FHIR_PAGE_SIZE_DEFAULT);
    }

    @Test
    public void testDefaultMaxPageSize() {
        assertEquals(ctx.getMaxPageSize(), FHIRConstants.FHIR_PAGE_SIZE_DEFAULT_MAX);
    }

    @Test
    public void testDefaultMaxPageIncludeCount() {
        assertEquals(ctx.getMaxPageIncludeCount(), FHIRConstants.FHIR_PAGE_INCLUDE_COUNT_DEFAULT_MAX);
    }

    @Test
    public void testDefaultLastPageNumber() {
        assertEquals(ctx.getLastPageNumber(), Integer.MAX_VALUE);
    }

    @Test
    public void testDefaultTotalCountIsNull() {
        assertNull(ctx.getTotalCount());
    }

    @Test
    public void testDefaultMatchCount() {
        assertEquals(ctx.getMatchCount(), 0);
    }

    @Test
    public void testDefaultLenientIsTrue() {
        assertTrue(ctx.isLenient());
    }

    @Test
    public void testDefaultFirstIdIsNull() {
        assertNull(ctx.getFirstId());
    }

    @Test
    public void testDefaultLastIdIsNull() {
        assertNull(ctx.getLastId());
    }

    // --- setters / getters ---

    @Test
    public void testSetAndGetPageNumber() {
        ctx.setPageNumber(5);
        assertEquals(ctx.getPageNumber(), 5);
    }

    @Test
    public void testSetAndGetPageSize() {
        ctx.setPageSize(50);
        assertEquals(ctx.getPageSize(), 50);
    }

    @Test
    public void testSetAndGetMaxPageSize() {
        ctx.setMaxPageSize(500);
        assertEquals(ctx.getMaxPageSize(), 500);
    }

    @Test
    public void testSetAndGetMaxPageIncludeCount() {
        ctx.setMaxPageIncludeCount(200);
        assertEquals(ctx.getMaxPageIncludeCount(), 200);
    }

    @Test
    public void testSetAndGetLastPageNumber() {
        ctx.setLastPageNumber(10);
        assertEquals(ctx.getLastPageNumber(), 10);
    }

    @Test
    public void testSetAndGetTotalCount() {
        ctx.setTotalCount(42);
        assertEquals((int) ctx.getTotalCount(), 42);
    }

    @Test
    public void testSetAndGetMatchCount() {
        ctx.setMatchCount(7);
        assertEquals(ctx.getMatchCount(), 7);
    }

    @Test
    public void testSetLenientFalse() {
        ctx.setLenient(false);
        assertFalse(ctx.isLenient());
    }

    @Test
    public void testSetLenientTrue() {
        ctx.setLenient(false);
        ctx.setLenient(true);
        assertTrue(ctx.isLenient());
    }

    @Test
    public void testSetAndGetFirstId() {
        ctx.setFirstId(1001L);
        assertEquals((long) ctx.getFirstId(), 1001L);
    }

    @Test
    public void testSetAndGetLastId() {
        ctx.setLastId(9999L);
        assertEquals((long) ctx.getLastId(), 9999L);
    }

    @Test
    public void testSetFirstIdNull() {
        ctx.setFirstId(100L);
        ctx.setFirstId(null);
        assertNull(ctx.getFirstId());
    }

    @Test
    public void testSetLastIdNull() {
        ctx.setLastId(100L);
        ctx.setLastId(null);
        assertNull(ctx.getLastId());
    }
}
