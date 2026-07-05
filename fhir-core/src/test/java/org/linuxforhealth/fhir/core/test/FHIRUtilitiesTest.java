/*
 * (C) Copyright IBM Corp. 2024
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.linuxforhealth.fhir.core.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.testng.annotations.Test;

import org.linuxforhealth.fhir.core.FHIRUtilities;

/**
 * Unit tests for {@link FHIRUtilities}.
 *
 * Note: {@code formatTimestamp} is tested in TimestampTest. This class covers the remaining methods.
 */
public class FHIRUtilitiesTest {

    // --- getObjectHandle ---

    @Test
    public void testGetObjectHandleReturnsNonNull() {
        Object o = new Object();
        String handle = FHIRUtilities.getObjectHandle(o);
        assertNotNull(handle);
    }

    @Test
    public void testGetObjectHandleIsHex() {
        Object o = new Object();
        String handle = FHIRUtilities.getObjectHandle(o);
        // Must be a valid hex string (digits 0-9 and a-f only)
        assertTrue(handle.matches("[0-9a-f]+"), "Expected hex string, got: " + handle);
    }

    // --- isEncoded ---

    @Test
    public void testIsEncodedWithNull() {
        assertFalse(FHIRUtilities.isEncoded(null));
    }

    @Test
    public void testIsEncodedWithPlainString() {
        assertFalse(FHIRUtilities.isEncoded("plaintext"));
    }

    @Test
    public void testIsEncodedWithEmptyString() {
        assertFalse(FHIRUtilities.isEncoded(""));
    }

    @Test
    public void testIsEncodedWithXorPrefix() {
        // A string starting with "{xor}" must be considered encoded
        assertTrue(FHIRUtilities.isEncoded("{xor}abc123"));
    }

    // --- decode ---

    @Test
    public void testDecodeNonEncodedStringIsPassthrough() {
        // A string without the "{xor}" prefix must be returned unchanged
        String input = "my-plaintext-password";
        assertEquals(FHIRUtilities.decode(input), input);
    }

    @Test
    public void testDecodeEncodedStringRoundTrip() {
        // Produce an XOR-encoded value manually:
        //   encode("test") = "{xor}" + Base64(XOR(bytes("test"), 0x5F))
        // XOR each byte of "test" with 0x5F:
        //   't'(0x74) ^ 0x5F = 0x2B  '+' 
        //   'e'(0x65) ^ 0x5F = 0x3A  ':'
        //   's'(0x73) ^ 0x5F = 0x2C  ','
        //   't'(0x74) ^ 0x5F = 0x2B  '+'
        // Base64("+:,+") = "KzosKw=="
        String encoded = "{xor}KzosKw==";
        assertEquals(FHIRUtilities.decode(encoded), "test");
    }

    // --- stripNamespaceIfPresentInDiv ---

    @Test
    public void testStripNamespaceNotPresent() {
        String input = "<div>some content</div>";
        assertEquals(FHIRUtilities.stripNamespaceIfPresentInDiv(input), input);
    }

    @Test
    public void testStripNamespacePresent() {
        String input = "<div xmlns=\"http://www.w3.org/1999/xhtml\">content</div>";
        String result = FHIRUtilities.stripNamespaceIfPresentInDiv(input);
        assertEquals(result, "<div>content</div>");
    }

    @Test
    public void testStripNamespaceEmptyString() {
        assertEquals(FHIRUtilities.stripNamespaceIfPresentInDiv(""), "");
    }

    // --- stripNewLineWhitespaceIfPresentInDiv ---

    @Test
    public void testStripNewLineNoDivPresent() {
        String input = "<p>hello</p>";
        assertEquals(FHIRUtilities.stripNewLineWhitespaceIfPresentInDiv(input), input);
    }

    @Test
    public void testStripNewLineDivWithNewlines() {
        // The method replaces \r and \n literal escape sequences (not actual newline chars)
        String input = "<div>line1\\nline2</div>";
        String result = FHIRUtilities.stripNewLineWhitespaceIfPresentInDiv(input);
        assertFalse(result.contains("\\n"), "Result must not contain literal \\n");
    }

    @Test
    public void testStripNewLineDivNoEndDiv() {
        // No closing </div> — method must return input unchanged
        String input = "<div>content without closing";
        assertEquals(FHIRUtilities.stripNewLineWhitespaceIfPresentInDiv(input), input);
    }

    // --- convertToTimestamp ---

    @Test
    public void testConvertToTimestampReturnsNonNull() {
        ZonedDateTime zdt = ZonedDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneId.of("UTC"));
        Timestamp ts = FHIRUtilities.convertToTimestamp(zdt);
        assertNotNull(ts);
    }

    @Test
    public void testConvertToTimestampCorrectValue() {
        ZonedDateTime zdt = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));
        Timestamp ts = FHIRUtilities.convertToTimestamp(zdt);
        // 2024-01-01T00:00:00Z in epoch millis
        assertEquals(ts.toInstant(), zdt.toInstant());
    }
}
