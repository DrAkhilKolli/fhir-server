#!/bin/bash
# ============================================================
# Clinivault FHIR Schema Deployment → Supabase
# ============================================================
# Deploys the LinuxForHealth FHIR R4 schema into Supabase
# Uses the IPv6 direct connection (db.*.supabase.co:5432)
# Run from the fhir-persistence-schema directory
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/target/fhir-persistence-schema-5.0.0-SNAPSHOT-cli.jar"
PROPS="$SCRIPT_DIR/supabase.properties"
SCHEMA_NAME="fhirdata"
FHIR_USER="fhirserver"

# ─── Sanity checks ───────────────────────────────────────────
if [ ! -f "$JAR" ]; then
  echo "❌  CLI jar not found: $JAR"
  echo "    Run 'mvn package -pl fhir-persistence-schema -am -DskipTests' first."
  exit 1
fi

if ! java -version 2>/dev/null; then
  echo "❌  Java not found. Please install Java 11+."
  exit 1
fi

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║  Clinivault FHIR Schema → Supabase                   ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""
echo "  Target : $(grep db.host $PROPS | cut -d= -f2):$(grep db.port $PROPS | cut -d= -f2)"
echo "  Schema : $SCHEMA_NAME"
echo "  DB     : $(grep db.database $PROPS | cut -d= -f2)"
echo ""

# ─── Step 1: Create schemas ───────────────────────────────────
echo "▶  Step 1/2 — Creating schema '$SCHEMA_NAME'..."
java -jar "$JAR" \
  --db-type postgresql \
  --prop-file "$PROPS" \
  --schema-name "$SCHEMA_NAME" \
  --create-schemas

echo ""
echo "✅  Schema '$SCHEMA_NAME' created."
echo ""

# ─── Step 2: Deploy FHIR tables ──────────────────────────────
echo "▶  Step 2/2 — Deploying FHIR R4 tables and indexes..."
echo "   (This may take 1–2 minutes on first run)"
java -jar "$JAR" \
  --db-type postgresql \
  --prop-file "$PROPS" \
  --schema-name "$SCHEMA_NAME" \
  --update-schema \
  --pool-size 1

echo ""
echo "✅  FHIR schema deployed successfully to Supabase!"
echo ""
echo "   Next steps:"
echo "   1. Update fhir-server-config.json to point at Supabase"
echo "   2. Set datasource in configDropins/overrides/datasources.xml"
echo "   3. Restart the FHIR server"
echo ""
