#!/bin/sh
# Entrypoint for the ClinicalVault FHIR Server container.
# Renders fhir-server-config.json from its template before starting Liberty,
# so ABAC (and other) settings can be controlled via environment variables
# without rebuilding the image.

set -eu

CONFIG_TMPL="${SERVER_DIR}/config/default/fhir-server-config.json.tmpl"
CONFIG_OUT="${SERVER_DIR}/config/default/fhir-server-config.json"

# ── ABAC defaults ─────────────────────────────────────────────────────────────
export ABAC_ENABLED="${ABAC_ENABLED:-false}"
export ABAC_REQUIRE_TENANT="${ABAC_REQUIRE_TENANT:-false}"
export ABAC_REQUIRE_ORG="${ABAC_REQUIRE_ORG:-false}"
export ABAC_ALLOWED_PURPOSES="${ABAC_ALLOWED_PURPOSES:-}"
export ABAC_RESOURCE_TENANT_SYSTEM="${ABAC_RESOURCE_TENANT_SYSTEM:-https://linuxforhealth.org/fhir/abac/tenant}"
export ABAC_RESOURCE_ORG_SYSTEM="${ABAC_RESOURCE_ORG_SYSTEM:-https://linuxforhealth.org/fhir/abac/org}"

# ── Keycloak issuer ───────────────────────────────────────────────────────────
# Full realm base URL published in /.well-known/smart-configuration.
# Must be the public HTTPS URL (e.g. https://auth.fhirvault.com/realms/smart-fhir).
# Required: leave empty and the SMART discovery doc will have broken auth endpoints.
export KC_ISSUER="${KC_ISSUER:-}"

# ── Terminology / FHIR-MCP defaults ──────────────────────────────────────────
# Base URL of the FHIR-MCP service reachable from within this container.
# In ECS this is the public ALB URL for the mcp subdomain (e.g. https://mcp.fhirvault.com).
# If not set, remoteTermServiceProviders will have an empty base and be effectively disabled.
export FHIR_MCP_INTERNAL_URL="${FHIR_MCP_INTERNAL_URL:-}"

# ── Render config ─────────────────────────────────────────────────────────────
envsubst < "${CONFIG_TMPL}" > "${CONFIG_OUT}"

exec /opt/ibm/wlp/bin/server run defaultServer
