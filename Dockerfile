# ─────────────────────────────────────────────────────────────────────────────
# ClinicalVault FHIR Server – local development image
#
# Build context : fhir-server/   (workspace root of the FHIR module)
# Pre-requisite : Maven package must have been run once so that the Liberty
#                 server is assembled under:
#                   fhir-server-webapp/target/liberty/wlp/
#
# Features:
#   - Open Liberty 26.0.0.4 (same version used by the Maven build)
#   - PostgreSQL JDBC driver (42.7.11) for Supabase connectivity
#   - fhir-smart interceptor for SMART on FHIR authorization
#   - mpJwt configured for Keycloak JWT validation
#   - Multi-tenant config directory (mount as a volume for zero-rebuild
#     tenant onboarding)
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:11-jre-jammy

# ── Environment ───────────────────────────────────────────────────────────────
ENV WLP_DIR=/opt/ibm/wlp \
    SERVER_NAME=defaultServer
ENV SERVER_DIR=${WLP_DIR}/usr/servers/${SERVER_NAME}

USER root

# ── 1. Install curl (healthcheck + PostgreSQL driver download) and gettext-base
#    gettext-base provides envsubst, used by start.sh to render
#    fhir-server-config.json from the bundled template at container start.
RUN apt-get update -qq \
    && apt-get install -y --no-install-recommends curl gettext-base \
    && rm -rf /var/lib/apt/lists/*

# ── 2. Copy the pre-assembled Liberty runtime ─────────────────────────────────
#    The liberty-maven-plugin has already assembled the complete Liberty server,
#    including all required features, the WAR, and all module jars, under
#    fhir-server-webapp/target/liberty/wlp/.  We copy that exact runtime so
#    there is zero version mismatch between the server and the features.
COPY fhir-server-webapp/target/liberty/wlp/ ${WLP_DIR}/

# ── 2b. Replace loose app definition with the packaged WAR ───────────────────
#    The liberty-maven-plugin generates a 'loose app' XML (fhir-server.war.xml)
#    that contains absolute paths to the developer's local filesystem.  These
#    paths do not exist inside the container, so Liberty starts the app with an
#    empty classpath — no JAX-RS endpoints are registered and every request
#    falls through to the static-file handler.  Swap it for the actual WAR.
#
#    Also remove development-only config overrides:
#      datasource-derby.xml             – Derby datasource (overrides PostgreSQL)
#      liberty-plugin-variable-config.xml – DB_LOC pointing to Mac paths
#
#    And remove the fhir-bulkdata-webapp which requires object-storage (S3/MinIO)
#    and fails with a compile-error during ServiceLoader init, which cascades to
#    the main FHIR webapp if the bulkdata WAR is on the classpath.
COPY fhir-server-webapp/target/fhir-server.war \
     ${SERVER_DIR}/apps/fhir-server.war
RUN rm -f  ${SERVER_DIR}/apps/fhir-server.war.xml \
    && rm -f ${SERVER_DIR}/configDropins/overrides/datasource-derby.xml \
    && rm -f ${SERVER_DIR}/configDropins/overrides/liberty-plugin-variable-config.xml \
    && rm -f ${SERVER_DIR}/dropins/fhir-bulkdata-webapp.war \
    && rm -f ${SERVER_DIR}/configDropins/defaults/bulkdata.xml \
    && rm -rf ${SERVER_DIR}/apps/expanded/fhir-bulkdata-webapp.war

# ── 3. Apply our customised server configuration ──────────────────────────────
#    The source-tree versions of these files have been updated to:
#      server.xml           → mpJwt (Keycloak), PostgreSQL dataSource,
#                             openapi-3.1 feature, HTTP port 9080 enabled.
#      fhir-server-config   → OAuth URLs point at the 'keycloak' container,
#                             datasource uses jdbc/fhir-default_default (PG),
#                             SMART AuthzPolicyEnforcement interceptor enabled.
COPY fhir-server-webapp/src/main/liberty/config/server.xml \
     ${SERVER_DIR}/server.xml

# Copy the config template; start.sh renders it to fhir-server-config.json at startup.
COPY fhir-server-webapp/src/main/liberty/config/config/default/fhir-server-config.json.tmpl \
     ${SERVER_DIR}/config/default/fhir-server-config.json.tmpl

# Also copy the static fallback (used by Liberty during feature cache warm-up).
COPY fhir-server-webapp/src/main/liberty/config/config/default/fhir-server-config.json \
     ${SERVER_DIR}/config/default/fhir-server-config.json

COPY fhir-server-webapp/src/main/liberty/config/start.sh /start.sh

# ── 4. Add PostgreSQL JDBC driver ─────────────────────────────────────────────
#    The liberty-maven-plugin copies Derby but not PostgreSQL. We download
#    the same version declared in fhir-parent/pom.xml (42.7.11) at image-build
#    time so no local Maven cache is required.
RUN mkdir -p ${WLP_DIR}/usr/shared/resources/lib/postgresql \
    && curl -fsSL \
         "https://jdbc.postgresql.org/download/postgresql-42.7.11.jar" \
         -o "${WLP_DIR}/usr/shared/resources/lib/postgresql/postgresql.jar"

# ── 5. Security: non-root user ────────────────────────────────────────────────
RUN groupadd -g 1001 liberty 2>/dev/null || true \
    && useradd -u 1001 -g 1001 -s /bin/false -d ${WLP_DIR} liberty 2>/dev/null || true \
    && chown -R 1001:1001 ${WLP_DIR} \
    && chmod +x /start.sh

USER 1001

# ── Ports ─────────────────────────────────────────────────────────────────────
# 9080 – HTTP  (enabled in server.xml for local dev; disable for production)
# 9443 – HTTPS
EXPOSE 9080 9443

# ── Health check ──────────────────────────────────────────────────────────────
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=5 \
    CMD curl -sf http://localhost:9080/fhir-server/api/v4/metadata > /dev/null || exit 1

ENTRYPOINT ["/start.sh"]
