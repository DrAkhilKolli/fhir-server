# ----------------------------------------------------------------------------
# (C) Copyright IBM Corp. 2016, 2022
#
# SPDX-License-Identifier: Apache-2.0
# ----------------------------------------------------------------------------
# Stage: Base

FROM openliberty/open-liberty:24.0.0.12-kernel-slim-java21-openj9-ubi-minimal AS base

USER root
RUN microdnf install -y unzip
RUN install -d -o 1001 /opt/fhir-server
USER 1001

COPY target/fhir-server-distribution.zip /tmp/
RUN unzip -qq /tmp/fhir-server-distribution.zip -d /tmp && \
    /tmp/fhir-server-dist/install.sh /opt/ol && \
    mv /tmp/fhir-server-dist/tools /opt/fhir-server/tools
COPY src/main/docker/fhir-server/bootstrap.properties /opt/ol/wlp/usr/servers/defaultServer/
COPY src/main/docker/fhir-server/bootstrap.sh /opt/fhir-server/
# ----------------------------------------------------------------------------
# Stage: Runnable

FROM openliberty/open-liberty:24.0.0.12-kernel-slim-java21-openj9-ubi-minimal

ARG VERBOSE=true
ARG FHIR_SERVER_VERSION=5.0.0-SNAPSHOT

# The following labels are required:
LABEL name='LinuxForHealth FHIR Server'
LABEL version="$FHIR_SERVER_VERSION"
LABEL summary="LinuxForHealth FHIR Server with OpenJ9 and UBI 8"
LABEL description="The LinuxForHealth FHIR Server is a modular Java implementation of the HL7 FHIR specification that supports versions R4 and R4B with a focus on performance and configurability."

ENV FHIR_CONFIG_HOME=/opt/ol/wlp/usr/servers/defaultServer \
    WLP_LOGGING_CONSOLE_SOURCE=message,trace,accessLog,ffdc,audit \
    WLP_LOGGING_CONSOLE_LOGLEVEL=info \
    WLP_LOGGING_CONSOLE_FORMAT=SIMPLE \
    WLP_LOGGING_MESSAGE_SOURCE="" \
    WLP_LOGGING_MESSAGE_FORMAT=JSON \
    TRACE_FILE=stdout \
    TRACE_FORMAT=BASIC

COPY target/LICENSE /licenses/

COPY --chown=1001:0 --from=base /opt/ol/wlp/usr/servers/defaultServer/server.xml /opt/ol/wlp/usr/servers/defaultServer/
COPY --chown=1001:0 --from=base /opt/ol/wlp/usr/servers/defaultServer/configDropins /opt/ol/wlp/usr/servers/defaultServer/configDropins

RUN features.sh

COPY --chown=1001:0 --from=base /opt/ol/wlp/usr /opt/ol/wlp/usr

RUN configure.sh && \
    mkdir -p /output/bulkdata

COPY --chown=1001:0 --from=base /opt/fhir-server /opt/fhir-server

# Install AWS CLI v2 for Cloudflare R2 config sync at startup.
# Architecture is detected at build time so the image works on amd64 and arm64.
USER root
RUN ARCH=$(uname -m) && \
    microdnf install -y curl unzip python3 && \
    curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-${ARCH}.zip" -o /tmp/awscliv2.zip && \
    unzip -q /tmp/awscliv2.zip -d /tmp && \
    /tmp/aws/install && \
    rm -rf /tmp/aws /tmp/awscliv2.zip && \
    microdnf update -y && \
    microdnf clean all
USER 1001

RUN mkdir -p /config/configDropins/overrides && \
    chmod -R 775 /config/configDropins/overrides && \
    chmod -R 775 /opt/ol/wlp/usr/servers/defaultServer/configDropins/defaults

# Cloudflare R2 config-sync variables (set at runtime via -e or compose env):
#   R2_ENDPOINT, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY
#   R2_BUCKET (default: fhir0config), R2_PREFIX (default: fhir-tenant-configs)
# Supabase credentials (set at runtime):
#   SUPABASE_DEFAULT_DB_USER, SUPABASE_DEFAULT_DB_PASSWORD
# Keycloak variables (resolved by mpJwt-keycloak.xml):
#   KEYCLOAK_HOST, KEYCLOAK_FHIR_AUDIENCE

# This block ensures the latest OS packages are current.
# (AWS CLI and Python3 already installed above.)
USER root
RUN microdnf clean all
USER 1001

# Set the working directory to the liberty defaultServer
WORKDIR ${FHIR_CONFIG_HOME}

ENTRYPOINT ["/opt/fhir-server/bootstrap.sh"]
CMD ["/opt/ol/wlp/bin/server", "run", "defaultServer"]