# ─────────────────────────────────────────────────────────────────────────────
# ClinicalVault FHIR Server – local development image
#
# Build context : fhir-server/   (workspace root of the FHIR module)
#
# Features:
#   - Open Liberty 26.0.0.6 with Jakarta EE 10 (servlet-4.0, jaxrs-2.1)
#   - PostgreSQL JDBC driver (42.7.11) for Supabase connectivity
#   - fhir-smart interceptor for SMART on FHIR authorization
#   - mpJwt configured for Keycloak JWT validation
#   - Multi-tenant config directory (mount as a volume for zero-rebuild
#     tenant onboarding)
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

# ── Environment ───────────────────────────────────────────────────────────────
ENV WLP_DIR=/opt/wlp \
    SERVER_NAME=defaultServer \
    LIBERTY_VERSION=26.0.0.6
ENV SERVER_DIR=${WLP_DIR}/usr/servers/${SERVER_NAME} \
    FHIR_CONFIG_HOME=${WLP_DIR}/usr/servers/defaultServer/

USER root

# ── 1. Install curl (healthcheck + PostgreSQL driver download) and gettext-base
#    gettext-base provides envsubst, used by start.sh to render
#    fhir-server-config.json from the bundled template at container start.
RUN apt-get update -qq \
    && apt-get install -y --no-install-recommends curl gettext-base python3 unzip \
    && rm -rf /var/lib/apt/lists/*

# ── 2. Download and extract Open Liberty 26.0.0.6 ────────────────────────────
#    Download from Maven Central with the correct version that includes
#    Jakarta EE 10 features (servlet-4.0, jaxrs-2.1, etc.)
RUN mkdir -p /opt/ibm
RUN curl -#fSL "https://repo1.maven.org/maven2/io/openliberty/openliberty-runtime/${LIBERTY_VERSION}/openliberty-runtime-${LIBERTY_VERSION}.zip" \
         -o /tmp/liberty.zip
RUN unzip -q /tmp/liberty.zip -d /opt && \
    rm /tmp/liberty.zip && \
    ln -s /opt/wlp /opt/ibm/wlp

# ── 2b. Install required Jakarta EE 10 features via featureUtility ────────────
#    The bare Open Liberty runtime doesn't include features. We must install them
#    to match server.xml requirements: servlet-4.0, jaxrs-2.1, mpjwt-2.1, etc.
RUN set -eu; \
    output="$(${WLP_DIR}/bin/featureUtility installFeature \
      servlet-4.0 \
      jaxrs-2.1 \
      jsonp-1.1 \
      websocket-1.1 \
      mpJwt-1.2 \
      jdbc-4.1 \
      appSecurity-3.0 \
      localConnector-1.0 \
      transportSecurity-1.0 \
      --acceptLicense 2>&1)" \
    || rc=$?; \
    rc="${rc:-0}"; \
    printf '%s\n' "$output"; \
    if [ "$rc" -eq 22 ]; then \
      echo "$output" | grep -q 'CWWKF1250I: The following assets already exist'; \
      exit 0; \
    fi; \
    exit "$rc"

# ── 3. Copy the compiled FHIR server WAR ──────────────────────────────────────
#    The WAR is built by Maven during `mvn package` and placed at:
#      fhir-server-webapp/target/fhir-server.war
#    We deploy it to the Liberty apps/ directory.
COPY fhir-server-webapp/target/fhir-server.war \
     ${SERVER_DIR}/apps/fhir-server.war

# ── 4. Apply our customised server configuration ──────────────────────────────
#    The source-tree versions of these files have been updated to:
#      server.xml           → mpJwt (Keycloak), PostgreSQL dataSource,
#                             servlet-4.0, jaxrs-2.1 features (Jakarta EE 10)
#      fhir-server-config   → OAuth URLs point at the 'keycloak' container,
#                             datasource uses jdbc/fhir-default_default (PG),
#                             SMART AuthzPolicyEnforcement interceptor enabled.
COPY fhir-server-webapp/src/main/liberty/config/server.xml \
     ${SERVER_DIR}/server.xml

# ── Copy configDropins (authFilter, keystore, datasource, monitoring, etc.) ───
RUN mkdir -p ${SERVER_DIR}/configDropins/defaults ${SERVER_DIR}/configDropins/overrides
COPY fhir-server-webapp/src/main/liberty/config/configDropins/ \
     ${SERVER_DIR}/configDropins/

# ── Ensure config directory structure exists ────────────────────────────────────
RUN mkdir -p ${SERVER_DIR}/config/default

# Copy the config template; start.sh renders it to fhir-server-config.json at startup.
COPY fhir-server-webapp/src/main/liberty/config/config/default/fhir-server-config.json.tmpl \
     ${SERVER_DIR}/config/default/fhir-server-config.json.tmpl

# Also copy the static fallback (used by Liberty during feature cache warm-up).
COPY fhir-server-webapp/src/main/liberty/config/config/default/fhir-server-config.json \
     ${SERVER_DIR}/config/default/fhir-server-config.json

COPY fhir-server-webapp/src/main/liberty/config/start.sh /start.sh

# ── 5. Bake in the schema CLI jar ─────────────────────────────────────────────
#    This allows the same image to run DB schema migrations (used by the
#    docker-compose fhir-schema-init one-shot service) without needing a
#    separate image.  The entrypoint is overridden to /bin/bash -c for that
#    service; normal Liberty startup uses /start.sh as normal.
COPY fhir-persistence-schema/target/fhir-persistence-schema-*-cli.jar \
     /opt/fhir-schema-cli.jar

# ── 6. Add PostgreSQL JDBC driver ─────────────────────────────────────────────
#    Download the same version declared in fhir-parent/pom.xml (42.7.11) 
#    at image-build time to ensure container can connect to PostgreSQL.
RUN mkdir -p ${WLP_DIR}/usr/shared/resources/lib/postgresql \
    && curl -fsSL \
         "https://jdbc.postgresql.org/download/postgresql-42.7.11.jar" \
         -o "${WLP_DIR}/usr/shared/resources/lib/postgresql/postgresql.jar"

# ── 7. Security: non-root user ────────────────────────────────────────────────
RUN groupadd -g 1001 liberty 2>/dev/null || true \
    && useradd -u 1001 -g 1001 -s /bin/false -d ${WLP_DIR} liberty 2>/dev/null || true \
    && chown -R 1001:1001 ${WLP_DIR} \
    && chown 1001:1001 /opt/fhir-schema-cli.jar \
    && chmod +x /start.sh \
    && mkdir -p /opt/clinivault \
    && chown 1001:1001 /opt/clinivault

USER 1001

# ── Ports ─────────────────────────────────────────────────────────────────────
# 9080 – HTTP  (enabled in server.xml for local dev; disable for production)
# 9443 – HTTPS
EXPOSE 9080 9443

# ── Health check ──────────────────────────────────────────────────────────────
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=5 \
    CMD curl -sf http://localhost:9080/fhir-server/api/v4/metadata > /dev/null || exit 1

ENTRYPOINT ["/start.sh"]
