/*
 * (C) Copyright Clinivault Inc. 2024, 2026
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.clinivault.fhir.interceptors;

import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.List;

import org.linuxforhealth.fhir.config.FHIRRequestContext;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;

/**
 * JWT parsing utilities for extracting claims from incoming HTTP Authorization
 * headers without depending on fhir-smart or external JWT libraries.
 */
final class JwtUtils {

    private static final Logger LOG = Logger.getLogger(JwtUtils.class.getName());

    private JwtUtils() {
    }

    /**
     * Extracts JWT claims from the {@code Authorization: Bearer <token>} header
     * present in the current {@code FHIRRequestContext}.
     *
     * @return the claims JSON object, or {@code null} if no valid header/token
     */
    static JsonObject extractClaims() {
        try {
            List<String> authHeaders = FHIRRequestContext.get().getHttpHeaders().get("Authorization");
            if (authHeaders == null || authHeaders.isEmpty()) {
                return null;
            }

            String authHeader = authHeaders.get(0);
            if (!authHeader.toLowerCase().startsWith("bearer ")) {
                return null;
            }

            String token = authHeader.substring(7).trim();
            return decodeJwt(token);
        } catch (Exception e) {
            LOG.log(Level.FINE, "JwtUtils.extractClaims: failed to extract JWT", e);
            return null;
        }
    }

    /**
     * Decodes a JWT string (format: {@code header.payload.signature}) and returns
     * the claims (payload) as a JsonObject.
     *
     * <p>Does NOT validate signature — the FHIR server's mpJwt interceptor
     * validates auth tokens at the app-security level before these persistence
     * interceptors run.
     */
    static JsonObject decodeJwt(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                LOG.fine("JwtUtils.decodeJwt: token does not have 3 parts");
                return null;
            }

            // Decode the payload (part[1])
            String payload = parts[1];
            // Add padding if needed
            while (payload.length() % 4 != 0) {
                payload += "=";
            }

            byte[] decodedBytes = Base64.getDecoder().decode(payload);
            String json = new String(decodedBytes, StandardCharsets.UTF_8);

            // Parse JSON
            try (JsonReader reader = Json.createReader(new StringReader(json))) {
                jakarta.json.JsonValue value = reader.readValue();
                if (value instanceof JsonObject) {
                    return (JsonObject) value;
                }
            }
            return null;
        } catch (Exception e) {
            LOG.log(Level.FINE, "JwtUtils.decodeJwt: failed to decode token", e);
            return null;
        }
    }
}
