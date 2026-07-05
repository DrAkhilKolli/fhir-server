#!/usr/bin/env bash

# ----------------------------------------------------------------------------
# config-sync.sh
# Downloads tenant configurations from Cloudflare R2 (S3-compatible) at
# container startup before OpenLiberty boots.
#
# R2 bucket structure:
#   fhir-tenant-configs/
#     tenant-registry.json              — list of active tenants
#     mpJwt-tenants.xml                 — all Keycloak realm mpJwt elements
#     default/
#       fhir-server-config.json         — FHIR config for the default tenant
#       datasource.xml                  — Supabase datasource for default tenant
#     <tenantId>/
#       fhir-server-config.json         — per-tenant FHIR config
#       datasource.xml                  — per-tenant Supabase datasource
#
# Required environment variables:
#   R2_ENDPOINT          — https://<account_id>.r2.cloudflarestorage.com
#   R2_ACCESS_KEY_ID     — Cloudflare R2 access key ID
#   R2_SECRET_ACCESS_KEY — Cloudflare R2 secret access key
#
# Optional environment variables:
#   R2_BUCKET  (default: fhir0config)
#   R2_PREFIX  (default: fhir-tenant-configs)
# ----------------------------------------------------------------------------

set -e -o pipefail

SCRIPT_NAME="$(basename "${BASH_SOURCE[0]}")"

function info  { echo "${SCRIPT_NAME} - [INFO]:  $(date +"%Y-%m-%d_%T") - ${1}"; }
function warn  { echo "${SCRIPT_NAME} - [WARN]:  $(date +"%Y-%m-%d_%T") - ${1}" >&2; }
function error { echo "${SCRIPT_NAME} - [ERROR]: $(date +"%Y-%m-%d_%T") - ${1}" >&2; }

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
R2_ENDPOINT="${R2_ENDPOINT:-}"
R2_ACCESS_KEY_ID="${R2_ACCESS_KEY_ID:-}"
R2_SECRET_ACCESS_KEY="${R2_SECRET_ACCESS_KEY:-}"
R2_BUCKET="${R2_BUCKET:-fhir0config}"
R2_PREFIX="${R2_PREFIX:-fhir-tenant-configs}"

LIBERTY_SERVER_DIR="/opt/ol/wlp/usr/servers/defaultServer"
CONFIG_DIR="${LIBERTY_SERVER_DIR}/config"
OVERRIDES_DIR="${LIBERTY_SERVER_DIR}/configDropins/overrides"
REGISTRY_FILE="${LIBERTY_SERVER_DIR}/tenant-registry.json"

# ---------------------------------------------------------------------------
# Guard: skip entirely when R2 credentials are absent (local dev / CI)
# ---------------------------------------------------------------------------
if [[ -z "${R2_ENDPOINT}" || -z "${R2_ACCESS_KEY_ID}" || -z "${R2_SECRET_ACCESS_KEY}" ]]; then
    info "R2 credentials not configured — skipping config sync (using container defaults)"
    exit 0
fi

# ---------------------------------------------------------------------------
# AWS CLI environment for Cloudflare R2
# ---------------------------------------------------------------------------
export AWS_ACCESS_KEY_ID="${R2_ACCESS_KEY_ID}"
export AWS_SECRET_ACCESS_KEY="${R2_SECRET_ACCESS_KEY}"
export AWS_DEFAULT_REGION="auto"

# Shared helper: download one file from R2; silently skip if not found
function r2_download {
    local src="$1"   # key inside the bucket (no leading slash)
    local dst="$2"   # absolute destination path
    local dir
    dir="$(dirname "${dst}")"
    mkdir -p "${dir}"

    if aws s3 cp \
           --endpoint-url "${R2_ENDPOINT}" \
           "s3://${R2_BUCKET}/${src}" \
           "${dst}" \
           --quiet 2>/dev/null; then
        info "  Downloaded: ${src} → ${dst}"
    else
        warn "  Not found in R2: ${src} (keeping existing)"
    fi
}

info "Starting config sync from Cloudflare R2 bucket: ${R2_BUCKET}"

# ---------------------------------------------------------------------------
# 1. Download the global tenant registry
# ---------------------------------------------------------------------------
r2_download "${R2_PREFIX}/tenant-registry.json" "${REGISTRY_FILE}"

# ---------------------------------------------------------------------------
# 2. Download the shared mpJwt config (all Keycloak realms)
#    Overwrites configDropins/overrides/mpJwt-tenants.xml so Liberty hot-loads
#    the production realm list without rebuilding the image.
# ---------------------------------------------------------------------------
r2_download "${R2_PREFIX}/mpJwt-tenants.xml" "${OVERRIDES_DIR}/mpJwt-tenants.xml"

# ---------------------------------------------------------------------------
# 3. Download the default tenant config (Supabase yqtyrgovlmwfftpoxgux)
# ---------------------------------------------------------------------------
r2_download "${R2_PREFIX}/default/fhir-server-config.json" \
            "${CONFIG_DIR}/default/fhir-server-config.json"
r2_download "${R2_PREFIX}/default/datasource.xml" \
            "${OVERRIDES_DIR}/datasource-default.xml"

# ---------------------------------------------------------------------------
# 4. Iterate over additional tenants listed in tenant-registry.json
# ---------------------------------------------------------------------------
if [[ -f "${REGISTRY_FILE}" ]]; then
    # Extract enabled tenant IDs with python3 (available in the Liberty UBI image)
    TENANT_IDS=$(python3 - <<'PYEOF'
import json, sys
try:
    with open("${REGISTRY_FILE}") as f:
        registry = json.load(f)
    for t in registry.get("tenants", []):
        if t.get("enabled", True) and t.get("tenantId") != "default":
            print(t["tenantId"])
except Exception as e:
    sys.exit(0)
PYEOF
    )

    for TENANT_ID in ${TENANT_IDS}; do
        info "Syncing config for tenant: ${TENANT_ID}"
        r2_download "${R2_PREFIX}/${TENANT_ID}/fhir-server-config.json" \
                    "${CONFIG_DIR}/${TENANT_ID}/fhir-server-config.json"
        r2_download "${R2_PREFIX}/${TENANT_ID}/datasource.xml" \
                    "${OVERRIDES_DIR}/datasource-${TENANT_ID}.xml"
    done
fi

info "Config sync complete"
