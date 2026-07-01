/*
 * (C) Copyright IBM Corp. 2024
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.linuxforhealth.fhir.core.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import org.linuxforhealth.fhir.core.CachedObjectHolder;

/**
 * Unit tests for {@link CachedObjectHolder}.
 */
public class CachedObjectHolderTest {

    // temp file created by some tests; cleaned up in @AfterMethod
    private File tempFile;

    @AfterMethod
    public void cleanup() {
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
            tempFile = null;
        }
    }

    // --- constructor(T) — no filename ---

    @Test
    public void testConstructorObjectOnly() {
        String value = "hello";
        CachedObjectHolder<String> holder = new CachedObjectHolder<>(value);
        assertEquals(holder.getCachedObject(), value);
        assertNull(holder.getFileName());
        assertEquals(holder.getLastModified(), 0L);
    }

    @Test
    public void testConstructorObjectOnlyIsNotStale() {
        // Without a filename, isStale() must return false
        CachedObjectHolder<Integer> holder = new CachedObjectHolder<>(42);
        assertFalse(holder.isStale());
    }

    // --- constructor(String, T) — with filename ---

    @Test
    public void testConstructorWithExistingFile() throws IOException {
        tempFile = File.createTempFile("cached-holder-test", ".tmp");
        tempFile.deleteOnExit();

        CachedObjectHolder<String> holder = new CachedObjectHolder<>(tempFile.getAbsolutePath(), "data");
        assertEquals(holder.getFileName(), tempFile.getAbsolutePath());
        assertEquals(holder.getCachedObject(), "data");
        // lastModified must be the file's actual last-modified time
        assertEquals(holder.getLastModified(), tempFile.lastModified());
    }

    @Test
    public void testConstructorWithNonExistentFile() {
        // File does not exist — lastModified will be 0
        String nonExistent = "/tmp/this-file-does-not-exist-cached-holder-test.tmp";
        CachedObjectHolder<String> holder = new CachedObjectHolder<>(nonExistent, "value");
        assertEquals(holder.getFileName(), nonExistent);
        assertEquals(holder.getLastModified(), 0L);
    }

    // --- isStale() ---

    @Test
    public void testIsStaleFileDoesNotExist() {
        // A non-existent file path — isStale returns true because !f.exists()
        CachedObjectHolder<String> holder = new CachedObjectHolder<>("/tmp/no-such-file-cached-test.tmp", "x");
        assertTrue(holder.isStale());
    }

    @Test
    public void testIsStaleFileExistsNotModified() throws IOException {
        tempFile = File.createTempFile("cached-holder-fresh", ".tmp");
        tempFile.deleteOnExit();

        CachedObjectHolder<String> holder = new CachedObjectHolder<>(tempFile.getAbsolutePath(), "data");
        // File exists and lastModified is the same as what we read — not stale
        assertFalse(holder.isStale());
    }

    @Test
    public void testIsStaleFileModifiedAfterConstruction() throws Exception {
        tempFile = File.createTempFile("cached-holder-stale", ".tmp");
        tempFile.deleteOnExit();

        CachedObjectHolder<String> holder = new CachedObjectHolder<>(tempFile.getAbsolutePath(), "data");

        // Ensure a different last-modified time by sleeping 10ms and then touching
        Thread.sleep(50);
        Files.write(tempFile.toPath(), "new content".getBytes());
        // Force lastModified to update (some OS may cache; explicitly set)
        tempFile.setLastModified(System.currentTimeMillis());

        assertTrue(holder.isStale());
    }

    @Test
    public void testIsStaleNullFileName() {
        // When fileName is null, isStale() must return false
        CachedObjectHolder<String> holder = new CachedObjectHolder<>("value");
        assertFalse(holder.isStale());
    }

    // --- setters / getters ---

    @Test
    public void testSetCachedObject() {
        CachedObjectHolder<String> holder = new CachedObjectHolder<>("initial");
        holder.setCachedObject("updated");
        assertEquals(holder.getCachedObject(), "updated");
    }

    @Test
    public void testSetFileName() {
        CachedObjectHolder<String> holder = new CachedObjectHolder<>("value");
        holder.setFileName("/some/path");
        assertEquals(holder.getFileName(), "/some/path");
    }

    @Test
    public void testSetLastModified() {
        CachedObjectHolder<String> holder = new CachedObjectHolder<>("value");
        long ts = 1_700_000_000_000L;
        holder.setLastModified(ts);
        assertEquals(holder.getLastModified(), ts);
    }

    @Test
    public void testGenericTypeInteger() {
        CachedObjectHolder<Integer> holder = new CachedObjectHolder<>(99);
        assertEquals((int) holder.getCachedObject(), 99);
    }
}
