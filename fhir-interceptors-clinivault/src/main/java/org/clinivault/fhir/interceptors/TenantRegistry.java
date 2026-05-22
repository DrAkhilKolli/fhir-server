/*
 * (C) Copyright Clinivault Inc. 2024, 2026
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.clinivault.fhir.interceptors;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

/**
 * Loads and caches the per-tenant registry from
 * {@code /opt/clinivault/tenant-registry.json}.
 *
 * <p>The registry JSON is a top-level array (or an object with a {@code "tenants"} key)
 * where each element has at minimum:
 * <pre>
 * {
 *   "tenant_id":  "acme-001",
 *   "realm":      "acme-realm",
 *   "dsid":       "default",
 *   "allowed_dsids": ["default"],
 *   "issuer":     "https://auth.example.com/realms/acme-realm",
 *   "jwks_uri":   "https://auth.example.com/realms/acme-realm/protocol/openid-connect/certs"
 * }
 * </pre>
 *
 * <p>When no registry file is present the instance is empty; interceptors fall
 * back to single-tenant mode (only the KC_ISSUER env-var realm is accepted).
 */
public final class TenantRegistry {

    private static final Logger LOG = Logger.getLogger(TenantRegistry.class.getName());
    static final String DEFAULT_REGISTRY_PATH = "/opt/clinivault/tenant-registry.json";

    /** Immutable snapshot of a single tenant entry. */
    public static final class Entry {
        public final String tenantId;
        public final String realm;
        public final String dsid;
        public final List<String> allowedDsids;
        public final String issuer;
        public final String jwksUri;

        Entry(String tenantId, String realm, String dsid, List<String> allowedDsids,
                String issuer, String jwksUri) {
            this.tenantId    = tenantId;
            this.realm       = realm;
            this.dsid        = dsid;
            this.allowedDsids = Collections.unmodifiableList(new ArrayList<>(allowedDsids));
            this.issuer      = issuer;
            this.jwksUri     = jwksUri;
        }
    }

    // ---- Singleton ---------------------------------------------------------

    private static volatile TenantRegistry INSTANCE;

    /** Returns the shared instance, loading once on first call. */
    public static TenantRegistry getInstance() {
        if (INSTANCE == null) {
            synchronized (TenantRegistry.class) {
                if (INSTANCE == null) {
                    INSTANCE = load(DEFAULT_REGISTRY_PATH);
                }
            }
        }
        return INSTANCE;
    }

    /** Replace the shared instance — used for testing and SIGHUP reload. */
    static void setInstance(TenantRegistry registry) {
        synchronized (TenantRegistry.class) {
            INSTANCE = registry;
        }
    }

    // ---- Instance ----------------------------------------------------------

    private final Map<String, Entry> byTenantId;
    private final Map<String, Entry> byRealm;

    private TenantRegistry(List<Entry> entries) {
        Map<String, Entry> tid  = new HashMap<>();
        Map<String, Entry> rlm  = new HashMap<>();
        for (Entry e : entries) {
            if (e.tenantId != null && !e.tenantId.isEmpty()) {
                tid.put(e.tenantId, e);
            }
            if (e.realm != null && !e.realm.isEmpty()) {
                rlm.put(e.realm, e);
            }
        }
        this.byTenantId = Collections.unmodifiableMap(tid);
        this.byRealm    = Collections.unmodifiableMap(rlm);
    }

    /** Look up an entry by its {@code tenant_id} field. */
    public Entry findByTenantId(String tenantId) {
        return byTenantId.get(tenantId);
    }

    /** Look up an entry by its {@code realm} field. */
    public Entry findByRealm(String realm) {
        return byRealm.get(realm);
    }

    /** {@code true} if the registry has at least one entry. */
    public boolean isEmpty() {
        return byTenantId.isEmpty();
    }

    // ---- Loader ------------------------------------------------------------

    static TenantRegistry load(String path) {
        Path p = Paths.get(path);
        if (!Files.exists(p)) {
            LOG.warning("TenantRegistry: " + path + " not found — running in single-tenant fallback mode");
            return new TenantRegistry(Collections.emptyList());
        }
        try (InputStream is = Files.newInputStream(p)) {
            return parse(is);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "TenantRegistry: failed to read " + path, e);
            return new TenantRegistry(Collections.emptyList());
        }
    }

    static TenantRegistry parse(InputStream is) {
        List<Entry> entries = new ArrayList<>();
        try (JsonReader reader = Json.createReader(is)) {
            jakarta.json.JsonValue root = reader.readValue();
            JsonArray arr;
            if (root instanceof JsonArray) {
                arr = (JsonArray) root;
            } else if (root instanceof JsonObject) {
                JsonObject obj = (JsonObject) root;
                arr = obj.containsKey("tenants") ? obj.getJsonArray("tenants") : Json.createArrayBuilder().build();
            } else {
                arr = Json.createArrayBuilder().build();
            }
            for (jakarta.json.JsonValue v : arr) {
                if (!(v instanceof JsonObject)) continue;
                JsonObject t     = (JsonObject) v;
                String tenantId  = t.getString("tenant_id", "");
                String realm     = t.getString("realm", "");
                String dsid      = t.getString("dsid", "default");
                String issuer    = t.getString("issuer", "");
                String jwksUri   = t.getString("jwks_uri", "");
                List<String> allowedDsids = new ArrayList<>();
                if (t.containsKey("allowed_dsids")) {
                    for (jakarta.json.JsonValue av : t.getJsonArray("allowed_dsids")) {
                        allowedDsids.add(av.toString().replace("\"", ""));
                    }
                } else {
                    allowedDsids.add(dsid);
                }
                entries.add(new Entry(tenantId, realm, dsid, allowedDsids, issuer, jwksUri));
            }
        }
        LOG.info("TenantRegistry: loaded " + entries.size() + " tenant(s)");
        return new TenantRegistry(entries);
    }
}
