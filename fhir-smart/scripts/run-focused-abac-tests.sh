#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MODULE_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
REPO_ROOT=$(CDPATH= cd -- "$MODULE_DIR/.." && pwd)
PARENT_POM="$REPO_ROOT/fhir-parent/pom.xml"

cd "$REPO_ROOT"

if [[ -x "./mvnw" ]]; then
	./mvnw -f "$PARENT_POM" -pl ../fhir-smart -am \
		-Dtest=AuthzAbacFocusedTest \
		-DfailIfNoTests=false \
		-Dsurefire.failIfNoSpecifiedTests=false \
		test
else
	mvn -f "$PARENT_POM" -pl ../fhir-smart -am \
		-Dtest=AuthzAbacFocusedTest \
		-DfailIfNoTests=false \
		-Dsurefire.failIfNoSpecifiedTests=false \
		test
fi
