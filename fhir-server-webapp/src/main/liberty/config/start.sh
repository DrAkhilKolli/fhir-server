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
export CLINIVAULT_TENANT_CONFIG_R2_REGION="${CLINIVAULT_TENANT_CONFIG_R2_REGION:-auto}"
export CLINIVAULT_TENANT_MPJWT_URL="${CLINIVAULT_TENANT_MPJWT_URL:-}"
if [ -z "${CLINIVAULT_TENANT_CONFIG_R2_ENDPOINT}" ] && [ "${CLINIVAULT_TENANT_CONFIG_STORAGE_PROVIDER}" = "r2" ] && [ -n "${CLOUDFLARE_ACCOUNT_ID:-}" ]; then
    export CLINIVAULT_TENANT_CONFIG_R2_ENDPOINT="https://${CLOUDFLARE_ACCOUNT_ID}.r2.cloudflarestorage.com"
fi

if [ -z "${AWS_ACCESS_KEY_ID:-}" ] && [ -n "${CLOUDFLARE_R2_ACCESS_KEY_ID:-}" ]; then
    export AWS_ACCESS_KEY_ID="${CLOUDFLARE_R2_ACCESS_KEY_ID}"
fi
if [ -z "${AWS_SECRET_ACCESS_KEY:-}" ] && [ -n "${CLOUDFLARE_R2_SECRET_ACCESS_KEY:-}" ]; then
    export AWS_SECRET_ACCESS_KEY="${CLOUDFLARE_R2_SECRET_ACCESS_KEY}"
fi

TENANT_CONFIG_BUCKET="${CLINIVAULT_TENANT_CONFIG_S3_BUCKET:-}"
TENANT_CONFIG_PREFIX="${CLINIVAULT_TENANT_CONFIG_S3_PREFIX:-${TENANT_FHIR_CONFIG_S3_PREFIX:-fhir-tenant-configs}}"

# Python-based SigV4 S3/R2 downloader — no aws CLI binary required.
# Falls back to aws CLI if python3 is somehow unavailable.
_s3_download_python() {
    # $1 = s3://bucket/key  $2 = local dest path
    python3 - "$1" "$2" << 'SIGV4EOF'
import sys, os, hashlib, hmac, datetime, urllib.request, urllib.error

def sign(key, msg):
    return hmac.new(key, msg.encode('utf-8'), hashlib.sha256).digest()
def get_signing_key(secret, date, region, service):
    k = sign(("AWS4" + secret).encode('utf-8'), date)
    k = sign(k, region); k = sign(k, service); return sign(k, "aws4_request")

def s3_get(s3_url, dest, endpoint=None, region="us-east-1", key_id="", secret=""):
    # parse s3://bucket/key
    s3_url = s3_url.strip()
    assert s3_url.startswith("s3://"), f"Not an s3:// URL: {s3_url}"
    rest = s3_url[5:]
    bucket, _, obj_key = rest.partition("/")
    if not endpoint:
        endpoint = f"https://s3.{region}.amazonaws.com"
    url = f"{endpoint.rstrip('/')}/{bucket}/{obj_key}"
    now = datetime.datetime.utcnow()
    amz_date = now.strftime("%Y%m%dT%H%M%SZ")
    date_stamp = now.strftime("%Y%m%d")
    host = url.split("/")[2]
    canonical = (
        f"GET\n/{bucket}/{obj_key}\n\n"
        f"host:{host}\nx-amz-content-sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\n"
        f"x-amz-date:{amz_date}\n\n"
        "host;x-amz-content-sha256;x-amz-date\n"
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    )
    cred_scope = f"{date_stamp}/{region}/s3/aws4_request"
    string_to_sign = f"AWS4-HMAC-SHA256\n{amz_date}\n{cred_scope}\n{hashlib.sha256(canonical.encode()).hexdigest()}"
    sig = hmac.new(get_signing_key(secret, date_stamp, region, "s3"),
                   string_to_sign.encode(), hashlib.sha256).hexdigest()
    auth = (f"AWS4-HMAC-SHA256 Credential={key_id}/{cred_scope},"
            "SignedHeaders=host;x-amz-content-sha256;x-amz-date,"
            f"Signature={sig}")
    req = urllib.request.Request(url, headers={
        "Host": host,
        "x-amz-date": amz_date,
        "x-amz-content-sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "Authorization": auth,
    })
    os.makedirs(os.path.dirname(dest) if os.path.dirname(dest) else ".", exist_ok=True)
    with urllib.request.urlopen(req) as resp, open(dest, "wb") as f:
        f.write(resp.read())

s3_url, dest = sys.argv[1], sys.argv[2]
endpoint = os.environ.get("CLINIVAULT_TENANT_CONFIG_R2_ENDPOINT") or os.environ.get("AWS_ENDPOINT_URL_S3") or ""
region   = os.environ.get("AWS_DEFAULT_REGION", "us-east-1")
key_id   = os.environ.get("AWS_ACCESS_KEY_ID", "")
secret   = os.environ.get("AWS_SECRET_ACCESS_KEY", "")
try:
    s3_get(s3_url, dest, endpoint or None, region, key_id, secret)
except urllib.error.HTTPError as e:
    print(f"[s3dl] HTTP {e.code} {e.reason} — {s3_url}", flush=True)
    sys.exit(1)
except Exception as e:
    print(f"[s3dl] Error: {e} — {s3_url}", flush=True)
    sys.exit(1)
SIGV4EOF
}

storage_aws() {
    # storage_aws s3 cp <src_url> <dest_path>  — downloads via Python SigV4
    if [ "${1}" = "s3" ] && [ "${2}" = "cp" ]; then
        _s3_download_python "${3}" "${4}"
        return $?
    fi
    # Fallback: use aws CLI for any other subcommands (e.g. aws s3 ls)
    if [ "${CLINIVAULT_TENANT_CONFIG_STORAGE_PROVIDER}" = "r2" ]; then
        if [ -z "${CLINIVAULT_TENANT_CONFIG_R2_ENDPOINT:-}" ]; then
            echo "[start.sh] R2 storage selected but CLINIVAULT_TENANT_CONFIG_R2_ENDPOINT is missing" >&2
            return 1
        fi
        aws --region "${CLINIVAULT_TENANT_CONFIG_R2_REGION:-auto}" --endpoint-url "${CLINIVAULT_TENANT_CONFIG_R2_ENDPOINT}" "$@"
        return $?
    fi
    aws "$@"
}

download_first_storage_url() {
    label="$1"
    dest_path="$2"
    shift 2

    mkdir -p "$(dirname "${dest_path}")"

    for candidate_url in "$@"; do
        [ -n "${candidate_url}" ] || continue

        storage_output="$(storage_aws s3 cp "${candidate_url}" "${dest_path}" 2>&1)"
        storage_status=$?
        if [ -n "${storage_output}" ]; then
            printf '%s\n' "${storage_output}" | sed "s/^/[start.sh] ${label}: /"
        fi

        if [ "${storage_status}" -eq 0 ]; then
            printf '%s\n' "${candidate_url}"
            return 0
        fi
    done

    return 1
}

# ── Tenant registry (shared-SaaS mode) ───────────────────────────────────────
# In shared-SaaS mode, CLINIVAULT_TENANT_REGISTRY_URL points at the registry
# that lists all registered realms. start.sh downloads it before generating the
# mpJwt dropin so that every realm's JWKS URI is trusted on first request.
REGISTRY_FILE="/opt/clinivault/tenant-registry.json"
ACTIVE_REGISTRY_URL=""
REGISTRY_ROOT_URL=""
REGISTRY_PREFIX_URL=""
if [ -n "${TENANT_CONFIG_BUCKET}" ]; then
    REGISTRY_ROOT_URL="s3://${TENANT_CONFIG_BUCKET}/tenant-registry.json"
    REGISTRY_PREFIX_URL="s3://${TENANT_CONFIG_BUCKET}/${TENANT_CONFIG_PREFIX}/tenant-registry.json"
fi

ACTIVE_REGISTRY_URL="$(download_first_storage_url \
    'storage-cp' \
    "${REGISTRY_FILE}" \
    "${CLINIVAULT_TENANT_REGISTRY_URL:-}" \
    "${REGISTRY_ROOT_URL}" \
    "${REGISTRY_PREFIX_URL}" || true)"

if [ -n "${ACTIVE_REGISTRY_URL}" ]; then
    export CLINIVAULT_TENANT_REGISTRY_URL="${ACTIVE_REGISTRY_URL}"
elif [ -n "${CLINIVAULT_TENANT_REGISTRY_URL:-}" ]; then
    echo "[start.sh] WARN: failed to download tenant registry from ${CLINIVAULT_TENANT_REGISTRY_URL}; continuing without shared registry" >&2
fi

# ── Per-realm mpJwt dropin generation (Option C) ─────────────────────────────
# Uses the object-store copy when present so rollout state is deterministic
# across tasks. Falls back to generating the dropin from tenant-registry.json.
# - Shared-SaaS mode: one <mpJwt> per tenant entry in the registry.
# - Dedicated mode:   one <mpJwt id="jwtDedicated"> from KC_ISSUER/KC_JWKS_URI.
DROPIN_DIR="${SERVER_DIR}/configDropins/overrides"
mkdir -p "${DROPIN_DIR}"
MPJWT_DROPIN="${DROPIN_DIR}/mpJwt-tenants.xml"
ACTIVE_MPJWT_URL=""
MPJWT_SAME_DIR_URL=""
MPJWT_ROOT_URL=""
MPJWT_PREFIX_URL=""
if [ -n "${ACTIVE_REGISTRY_URL}" ]; then
    MPJWT_SAME_DIR_URL="${ACTIVE_REGISTRY_URL%/*}/mpJwt-tenants.xml"
fi
if [ -n "${TENANT_CONFIG_BUCKET}" ]; then
    MPJWT_ROOT_URL="s3://${TENANT_CONFIG_BUCKET}/mpJwt-tenants.xml"
    MPJWT_PREFIX_URL="s3://${TENANT_CONFIG_BUCKET}/${TENANT_CONFIG_PREFIX}/mpJwt-tenants.xml"
fi

ACTIVE_MPJWT_URL="$(download_first_storage_url \
    'mpjwt-cp' \
    "${MPJWT_DROPIN}" \
    "${CLINIVAULT_TENANT_MPJWT_URL:-}" \
    "${MPJWT_SAME_DIR_URL}" \
    "${MPJWT_ROOT_URL}" \
    "${MPJWT_PREFIX_URL}" || true)"

if [ -n "${ACTIVE_MPJWT_URL}" ]; then
    export CLINIVAULT_TENANT_MPJWT_URL="${ACTIVE_MPJWT_URL}"
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
    if [ -n "${ACTIVE_MPJWT_URL}" ]; then
        echo "[start.sh] Using object-store mpJwt dropin from ${ACTIVE_MPJWT_URL}"
    else
        echo "[start.sh] Using existing mpJwt dropin at ${MPJWT_DROPIN}"
    fi
fi

# ── Per-tenant fhir-server-config.json and datasource.xml (shared-SaaS mode) ─
# For each tenant in the registry, download their fhir-server-config.json from object storage
# into ${SERVER_DIR}/config/${tenantId}/ and merge their datasource.xml entries
# into a single configDropins dropin so Liberty registers all JNDI datasources.
# This enables zero-image-rebuild tenant onboarding.
if [ -f "${REGISTRY_FILE}" ]; then
    python3 - << 'PYEOF'
import json, os, hashlib, hmac, datetime, urllib.request, urllib.error, sys

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

def _sign(key, msg):
    return hmac.new(key, msg.encode('utf-8'), hashlib.sha256).digest()
def _signing_key(secret, date, region, service):
    k = _sign(('AWS4' + secret).encode('utf-8'), date)
    k = _sign(k, region); k = _sign(k, service); return _sign(k, 'aws4_request')

def _s3_get(bucket_name, obj_key, dst_path, endpoint, region, key_id, secret):
    if not endpoint:
        endpoint = f'https://s3.{region}.amazonaws.com'
    url = f'{endpoint.rstrip("/")}/{bucket_name}/{obj_key}'
    now = datetime.datetime.utcnow()
    amz_date = now.strftime('%Y%m%dT%H%M%SZ')
    date_stamp = now.strftime('%Y%m%d')
    host = url.split('/')[2]
    empty = 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'
    canonical = (
        f'GET\n/{bucket_name}/{obj_key}\n\n'
        f'host:{host}\nx-amz-content-sha256:{empty}\nx-amz-date:{amz_date}\n\n'
        f'host;x-amz-content-sha256;x-amz-date\n{empty}'
    )
    cred_scope = f'{date_stamp}/{region}/s3/aws4_request'
    sts = f'AWS4-HMAC-SHA256\n{amz_date}\n{cred_scope}\n{hashlib.sha256(canonical.encode()).hexdigest()}'
    sig = hmac.new(_signing_key(secret, date_stamp, region, 's3'), sts.encode(), hashlib.sha256).hexdigest()
    auth = (f'AWS4-HMAC-SHA256 Credential={key_id}/{cred_scope},'
            f'SignedHeaders=host;x-amz-content-sha256;x-amz-date,Signature={sig}')
    req = urllib.request.Request(url, headers={
        'Host': host, 'x-amz-date': amz_date,
        'x-amz-content-sha256': empty, 'Authorization': auth,
    })
    os.makedirs(os.path.dirname(dst_path) if os.path.dirname(dst_path) else '.', exist_ok=True)
    with urllib.request.urlopen(req) as resp, open(dst_path, 'wb') as fh:
        fh.write(resp.read())

_r2_ep  = r2_endpoint or os.environ.get('AWS_ENDPOINT_URL_S3', '')
_region = os.environ.get('AWS_DEFAULT_REGION', 'us-east-1')
_key_id = os.environ.get('AWS_ACCESS_KEY_ID', '')
_secret = os.environ.get('AWS_SECRET_ACCESS_KEY', '')

def _s3_copy_first(bucket_name: str, keys, dst_path: str):
    endpoint = _r2_ep if storage_provider == 'r2' else ''
    for key in keys:
        try:
            _s3_get(bucket_name, key, dst_path, endpoint, _region, _key_id, _secret)
            return key
        except urllib.error.HTTPError as e:
            if e.code == 404:
                continue
            print(f'[start.sh] S3 HTTP {e.code} for {bucket_name}/{key}: {e.reason}', flush=True)
        except Exception as e:
            print(f'[start.sh] S3 error for {bucket_name}/{key}: {e}', flush=True)
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

# ── Default tenant datasource from FHIR_DB_* env vars ───────────────────────
# When FHIR_DB_SERVER is set, generate datasource-default.xml from env vars and
# remove the static datasources.xml (baked into the image with stale creds).
# This makes the default tenant datasource configurable without a rebuild.
if [ -n "${FHIR_DB_SERVER:-}" ]; then
python3 - << 'PYEOF'
import os
server_dir = os.environ.get('SERVER_DIR', '/opt/ibm/wlp/usr/servers/defaultServer')
dropin_dir = os.path.join(server_dir, 'configDropins', 'overrides')
os.makedirs(dropin_dir, exist_ok=True)

# Remove the image-baked static datasources.xml so Liberty doesn't load
# duplicate/stale connection details for the default tenant.
import os as _os
static_ds = os.path.join(dropin_dir, 'datasources.xml')
if _os.path.exists(static_ds):
    _os.remove(static_ds)
    print(f'[start.sh] Removed static datasources.xml (replaced by env-driven version)', flush=True)

db_server   = os.environ.get('FHIR_DB_SERVER', '')
db_port     = os.environ.get('FHIR_DB_PORT', '5432')
db_name     = os.environ.get('FHIR_DB_NAME', 'postgres')
db_user     = os.environ.get('FHIR_DB_USER', 'postgres')
db_pass     = os.environ.get('FHIR_DB_PASS', '')
db_schema   = os.environ.get('FHIR_DB_SCHEMA', 'fhirdata')
db_ssl_mode = os.environ.get('FHIR_DB_SSL_MODE', 'require')

xml = f'''<?xml version="1.0" encoding="UTF-8"?>
<!-- Auto-generated by start.sh from FHIR_DB_* env vars — do not edit -->
<server>
    <dataSource id="fhirDatasourceDefaultDefault" jndiName="jdbc/fhir-default_default" type="javax.sql.XADataSource" statementCacheSize="200" syncQueryTimeoutWithTransactionTimeout="true">
        <jdbcDriver javax.sql.XADataSource="org.postgresql.xa.PGXADataSource" libraryRef="sharedLibPostgres"/>
        <properties.postgresql
             serverName="{db_server}"
             portNumber="{db_port}"
             databaseName="{db_name}"
             user="{db_user}"
             password="{db_pass}"
             currentSchema="{db_schema}"
             ssl="true"
             sslMode="{db_ssl_mode}"
         />
        <connectionManager maxPoolSize="200" minPoolSize="20" connectionTimeout="60s" maxIdleTime="2m" numConnectionsPerThreadLocal="0"/>
    </dataSource>
    <dataSource id="fhirbatchDS" jndiName="jdbc/fhirbatchDB" type="javax.sql.XADataSource" statementCacheSize="200" syncQueryTimeoutWithTransactionTimeout="true">
        <jdbcDriver javax.sql.XADataSource="org.postgresql.xa.PGXADataSource" libraryRef="sharedLibPostgres"/>
        <properties.postgresql
            serverName="{db_server}"
            portNumber="{db_port}"
            databaseName="{db_name}"
            user="{db_user}"
            password="{db_pass}"
            currentSchema="{db_schema}"
            ssl="true"
            sslMode="{db_ssl_mode}"
          />
        <connectionManager maxPoolSize="10" minPoolSize="1" connectionTimeout="60s" maxIdleTime="2m"/>
    </dataSource>
</server>
'''

out_path = os.path.join(dropin_dir, 'datasource-default.xml')
with open(out_path, 'w') as f:
    f.write(xml)
print(f'[start.sh] Generated default datasource from FHIR_DB_* env vars -> {out_path} (server={db_server})', flush=True)
PYEOF
fi

# ── Render fhir-server-config.json ───────────────────────────────────────────
envsubst < "${CONFIG_TMPL}" > "${CONFIG_OUT}"

exec /opt/ibm/wlp/bin/server run defaultServer
