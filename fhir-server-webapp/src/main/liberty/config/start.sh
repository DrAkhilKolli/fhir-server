#!/bin/sh
# Entrypoint for the ClinicalVault FHIR Server container.
# 1. Optionally downloads the tenant registry from object storage (Cloudflare R2 or S3).
# 2. Downloads or generates configDropins/overrides/mpJwt-tenants.xml.
# 3. Downloads per-tenant fhir-server-config.json and datasource.xml from object storage.
# 4. Renders fhir-server-config.json from its template via envsubst.
# 5. Starts Open Liberty.

set -eu

CONFIG_TMPL="${SERVER_DIR}/config/default/fhir-server-config.json.tmpl"
CONFIG_OUT="${SERVER_DIR}/config/default/fhir-server-config.json"

# ── ABAC defaults (enabled in production) ────────────────────────────────────
export ABAC_ENABLED="${ABAC_ENABLED:-true}"
export ABAC_REQUIRE_TENANT="${ABAC_REQUIRE_TENANT:-true}"
export ABAC_REQUIRE_ORG="${ABAC_REQUIRE_ORG:-true}"
export ABAC_ALLOWED_PURPOSES="${ABAC_ALLOWED_PURPOSES:-TREAT,HPAYMT,HOPERAT}"
export ABAC_RESOURCE_TENANT_SYSTEM="${ABAC_RESOURCE_TENANT_SYSTEM:-https://linuxforhealth.org/fhir/abac/tenant}"
export ABAC_RESOURCE_ORG_SYSTEM="${ABAC_RESOURCE_ORG_SYSTEM:-https://linuxforhealth.org/fhir/abac/org}"

# ── Keycloak issuer ───────────────────────────────────────────────────────────
export KC_ISSUER="${KC_ISSUER:-}"
export KC_JWKS_URI="${KC_JWKS_URI:-${KC_ISSUER}/protocol/openid-connect/certs}"

# ── Terminology / FHIR-MCP defaults ──────────────────────────────────────────
export FHIR_MCP_INTERNAL_URL="${FHIR_MCP_INTERNAL_URL:-}"

# ── Tenant config storage ─────────────────────────────────────────────────────
export CLINIVAULT_TENANT_CONFIG_STORAGE_PROVIDER="${CLINIVAULT_TENANT_CONFIG_STORAGE_PROVIDER:-s3}"
export CLINIVAULT_TENANT_CONFIG_R2_ENDPOINT="${CLINIVAULT_TENANT_CONFIG_R2_ENDPOINT:-}"
if [ -z "${CLINIVAULT_TENANT_CONFIG_R2_ENDPOINT}" ] && [ "${CLINIVAULT_TENANT_CONFIG_STORAGE_PROVIDER}" = "r2" ] && [ -n "${CLOUDFLARE_ACCOUNT_ID:-}" ]; then
    export CLINIVAULT_TENANT_CONFIG_R2_ENDPOINT="https://${CLOUDFLARE_ACCOUNT_ID}.r2.cloudflarestorage.com"
fi

storage_aws() {
    if [ "${CLINIVAULT_TENANT_CONFIG_STORAGE_PROVIDER}" = "r2" ]; then
        if [ -z "${CLINIVAULT_TENANT_CONFIG_R2_ENDPOINT:-}" ]; then
            echo "[start.sh] R2 storage selected but CLINIVAULT_TENANT_CONFIG_R2_ENDPOINT/CLOUDFLARE_ACCOUNT_ID is missing" >&2
            return 1
        fi
        aws --endpoint-url "${CLINIVAULT_TENANT_CONFIG_R2_ENDPOINT}" "$@"
        return $?
    fi
    aws "$@"
}

# ── Tenant registry (shared-SaaS mode) ───────────────────────────────────────
# In shared-SaaS mode, CLINIVAULT_TENANT_REGISTRY_URL points at the registry
# that lists all registered realms. start.sh downloads it before generating the
# mpJwt dropin so that every realm's JWKS URI is trusted on first request.
REGISTRY_FILE="/opt/clinivault/tenant-registry.json"
if [ -n "${CLINIVAULT_TENANT_REGISTRY_URL:-}" ]; then
    mkdir -p "$(dirname "${REGISTRY_FILE}")"
    storage_aws s3 cp "${CLINIVAULT_TENANT_REGISTRY_URL}" "${REGISTRY_FILE}" \
        2>&1 | sed 's/^/[start.sh] storage-cp: /' || true
fi

# ── Per-realm mpJwt dropin generation (Option C) ─────────────────────────────
# Uses the object-store copy when present so rollout state is deterministic
# across tasks. Falls back to generating the dropin from tenant-registry.json.
# - Shared-SaaS mode: one <mpJwt> per tenant entry in the registry.
# - Dedicated mode:   one <mpJwt id="jwtDedicated"> from KC_ISSUER/KC_JWKS_URI.
DROPIN_DIR="${SERVER_DIR}/configDropins/overrides"
mkdir -p "${DROPIN_DIR}"
MPJWT_DROPIN="${DROPIN_DIR}/mpJwt-tenants.xml"
if [ -n "${CLINIVAULT_TENANT_REGISTRY_URL:-}" ]; then
    MPJWT_STORAGE_URL="${CLINIVAULT_TENANT_REGISTRY_URL%/*}/mpJwt-tenants.xml"
    storage_aws s3 cp "${MPJWT_STORAGE_URL}" "${MPJWT_DROPIN}" \
        2>&1 | sed 's/^/[start.sh] mpjwt-cp: /' || true
fi

if [ ! -s "${MPJWT_DROPIN}" ]; then
python3 - << 'PYEOF'
import json, os, sys

registry_file = '/opt/clinivault/tenant-registry.json'
server_dir    = os.environ.get('SERVER_DIR', '/opt/ibm/wlp/usr/servers/defaultServer')
dropin_out    = os.path.join(server_dir, 'configDropins', 'overrides', 'mpJwt-tenants.xml')
os.makedirs(os.path.dirname(dropin_out), exist_ok=True)

elements = []
if os.path.exists(registry_file):
    with open(registry_file) as f:
        data = json.load(f)
    tenants = data if isinstance(data, list) else data.get('tenants', [])
    for t in tenants:
        tid      = t.get('tenant_id', '')
        jwks_uri = t.get('jwks_uri', '')
        issuer   = t.get('issuer', jwks_uri.replace('/protocol/openid-connect/certs', ''))
        if tid and jwks_uri:
            safe_id = ''.join(c if c.isalnum() or c == '_' else '_' for c in tid)
            elements.append(
                f'    <mpJwt id="jwt_{safe_id}"\n'
                f'        issuer="{issuer}"\n'
                f'        jwksUri="{jwks_uri}"\n'
                f'        audiences="fhir-server"\n'
                f'        userNameAttribute="preferred_username"\n'
                f'        groupNameAttribute="groups"\n'
                f'        authFilterRef="fhirAuthFilter" />'
            )

if not elements:
    kc_issuer = os.environ.get('KC_ISSUER', '')
    kc_jwks   = os.environ.get('KC_JWKS_URI',
                    f'{kc_issuer}/protocol/openid-connect/certs' if kc_issuer else '')
    if kc_issuer and kc_jwks:
        elements.append(
            f'    <mpJwt id="jwtDedicated"\n'
            f'        issuer="{kc_issuer}"\n'
            f'        jwksUri="{kc_jwks}"\n'
            f'        audiences="fhir-server"\n'
            f'        userNameAttribute="preferred_username"\n'
            f'        groupNameAttribute="groups"\n'
            f'        authFilterRef="fhirAuthFilter" />'
        )

xml = (
    '<?xml version="1.0" encoding="UTF-8"?>\n'
    '<!-- Auto-generated by start.sh — do not edit. Regenerated on each container start. -->\n'
    '<server description="Per-realm mpJwt elements">\n'
    + '\n'.join(elements) + ('\n' if elements else '')
    + '</server>\n'
)

with open(dropin_out, 'w') as f:
    f.write(xml)

count = len(elements)
print(f'[start.sh] Generated {count} mpJwt element(s) -> {dropin_out}', flush=True)
if count == 0:
    print(
        '[start.sh] WARNING: No mpJwt elements generated. '
        'Set KC_ISSUER/KC_JWKS_URI or provide tenant-registry.json.',
        file=sys.stderr, flush=True,
    )
PYEOF
else
    echo "[start.sh] Using object-store mpJwt dropin at ${MPJWT_DROPIN}"
fi

# ── Per-tenant fhir-server-config.json and datasource.xml (shared-SaaS mode) ─
# For each tenant in the registry, download their fhir-server-config.json from object storage
# into ${SERVER_DIR}/config/${tenantId}/ and merge their datasource.xml entries
# into a single configDropins dropin so Liberty registers all JNDI datasources.
# This enables zero-image-rebuild tenant onboarding.
TENANT_CONFIG_BUCKET="${CLINIVAULT_TENANT_CONFIG_S3_BUCKET:-}"
TENANT_CONFIG_PREFIX="${CLINIVAULT_TENANT_CONFIG_S3_PREFIX:-${TENANT_FHIR_CONFIG_S3_PREFIX:-fhir-tenant-configs}}"
if [ -f "${REGISTRY_FILE}" ]; then
    python3 - << 'PYEOF'
import json, os, subprocess, sys

registry_file = '/opt/clinivault/tenant-registry.json'
server_dir    = os.environ.get('SERVER_DIR', '/opt/ibm/wlp/usr/servers/defaultServer')
bucket        = os.environ.get('CLINIVAULT_TENANT_CONFIG_S3_BUCKET', '').strip()
prefix        = (
    os.environ.get('CLINIVAULT_TENANT_CONFIG_S3_PREFIX', '').strip()
    or os.environ.get('TENANT_FHIR_CONFIG_S3_PREFIX', '').strip()
    or 'fhir-tenant-configs'
)
registry_url  = os.environ.get('CLINIVAULT_TENANT_REGISTRY_URL', '').strip()
storage_provider = os.environ.get('CLINIVAULT_TENANT_CONFIG_STORAGE_PROVIDER', 's3').strip().lower()
r2_endpoint = os.environ.get('CLINIVAULT_TENANT_CONFIG_R2_ENDPOINT', '').strip()
account_id = os.environ.get('CLOUDFLARE_ACCOUNT_ID', '').strip()
if not r2_endpoint and storage_provider == 'r2' and account_id:
    r2_endpoint = f'https://{account_id}.r2.cloudflarestorage.com'

if not bucket and (registry_url.startswith('s3://') or registry_url.startswith('r2://')):
    without_scheme = registry_url.split('://', 1)[1]
    slash = without_scheme.find('/')
    bucket = without_scheme if slash < 0 else without_scheme[:slash]
    print(f'[start.sh] Derived tenant-config bucket from registry URL: {bucket}', flush=True)

if not bucket:
    print('[start.sh] CLINIVAULT_TENANT_CONFIG_S3_BUCKET not set — skipping per-tenant config load', flush=True)
    sys.exit(0)

with open(registry_file) as f:
    data = json.load(f)
tenants = data if isinstance(data, list) else data.get('tenants', [])

datasource_fragments = []
loaded = 0

def _candidate_keys(tenant_id: str, file_name: str):
    keys = []
    if prefix:
        keys.append(f'{prefix}/{tenant_id}/{file_name}')
    keys.append(f'tenants/{tenant_id}/{file_name}')
    keys.append(f'{tenant_id}/{file_name}')
    deduped = []
    seen = set()
    for key in keys:
        normalized = key.strip('/')
        if normalized and normalized not in seen:
            seen.add(normalized)
            deduped.append(normalized)
    return deduped

def _s3_copy_first(bucket_name: str, keys, dst_path: str):
    for key in keys:
        s3_uri = f's3://{bucket_name}/{key}'
        cmd = ['aws']
        if storage_provider == 'r2':
            if not r2_endpoint:
                print('[start.sh] R2 storage selected but endpoint is missing', flush=True)
                return ''
            cmd += ['--endpoint-url', r2_endpoint]
        cmd += ['s3', 'cp', s3_uri, dst_path]
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode == 0:
            return key
    return ''

for t in tenants:
    tid = (t.get('tenant_id') or t.get('tenantId') or t.get('id') or '').strip()
    if not tid or tid == 'default':
        continue

    # ── fhir-server-config.json ──────────────────────────────────────────────
    config_dir = os.path.join(server_dir, 'config', tid)
    os.makedirs(config_dir, exist_ok=True)
    config_out = os.path.join(config_dir, 'fhir-server-config.json')
    config_keys = _candidate_keys(tid, 'fhir-server-config.json')
    config_key = _s3_copy_first(bucket, config_keys, config_out)
    if not config_key:
        attempted = ', '.join(config_keys)
        print(f'[start.sh] WARN: no fhir-server-config for {tid}; attempted keys: {attempted}', flush=True)
        continue

    # ── datasource.xml fragment ───────────────────────────────────────────────
    ds_tmp = f'/tmp/datasource_{tid}.xml'
    ds_keys = _candidate_keys(tid, 'datasource.xml')
    ds_key = _s3_copy_first(bucket, ds_keys, ds_tmp)
    if ds_key:
        with open(ds_tmp) as fh:
            content = fh.read()
        # Extract inner <dataSource ...>...</dataSource> elements (skip <server> wrapper)
        import re
        fragments = re.findall(r'<dataSource\b.*?</dataSource>', content, re.DOTALL)
        datasource_fragments.extend(fragments)
        os.remove(ds_tmp)
    else:
        attempted_ds = ', '.join(ds_keys)
        print(f'[start.sh] WARN: no datasource.xml for {tid}; attempted keys: {attempted_ds}', flush=True)

    loaded += 1

print(f'[start.sh] Loaded {loaded} per-tenant FHIR config(s) from {storage_provider}://{bucket}/{prefix}/', flush=True)

# ── Write merged datasource dropin ───────────────────────────────────────────
dropin_ds = os.path.join(server_dir, 'configDropins', 'overrides', 'datasource-tenants.xml')
os.makedirs(os.path.dirname(dropin_ds), exist_ok=True)
with open(dropin_ds, 'w') as fh:
    fh.write('<?xml version="1.0" encoding="UTF-8"?>\n')
    fh.write('<!-- Auto-generated by start.sh — per-tenant datasources -->\n')
    fh.write('<server>\n')
    for frag in datasource_fragments:
        fh.write(f'  {frag}\n')
    fh.write('</server>\n')
print(f'[start.sh] Wrote {len(datasource_fragments)} datasource fragment(s) -> {dropin_ds}', flush=True)
PYEOF
fi

# ── Render fhir-server-config.json ───────────────────────────────────────────
envsubst < "${CONFIG_TMPL}" > "${CONFIG_OUT}"

exec /opt/ibm/wlp/bin/server run defaultServer
