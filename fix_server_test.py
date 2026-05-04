#!/usr/bin/env python3
"""Fix R5 API changes in fhir-server-test files."""
import re

fixes = [
    # SearchRevIncludeTest.java
    {
        "file": "fhir-server-test/src/test/java/org/linuxforhealth/fhir/server/test/SearchRevIncludeTest.java",
        "replacements": [
            (".performed(DateTime", ".occurrence(DateTime"),
            ("EncounterStatus.FINISHED", "EncounterStatus.COMPLETED"),
            (".patient(Reference.builder().reference(of(\"Patient/\" + patient3Id)).build())",
             ".subject(Reference.builder().reference(of(\"Patient/\" + patient3Id)).build())"),
        ]
    },
    # SearchIncludeTest.java
    {
        "file": "fhir-server-test/src/test/java/org/linuxforhealth/fhir/server/test/SearchIncludeTest.java",
        "replacements": [
            (".performed(DateTime", ".occurrence(DateTime"),
            ("EncounterStatus.FINISHED", "EncounterStatus.COMPLETED"),
        ]
    },
    # SearchReverseChainTest.java
    {
        "file": "fhir-server-test/src/test/java/org/linuxforhealth/fhir/server/test/SearchReverseChainTest.java",
        "replacements": [
            (".performed(DateTime", ".occurrence(DateTime"),
            ("EncounterStatus.FINISHED", "EncounterStatus.COMPLETED"),
        ]
    },
    # ServerMeasureSubmitDataOperationTest.java
    {
        "file": "fhir-server-test/src/test/java/org/linuxforhealth/fhir/server/test/cqf/ServerMeasureSubmitDataOperationTest.java",
        "replacements": [
            ("EncounterStatus.FINISHED", "EncounterStatus.COMPLETED"),
        ]
    },
    # SearchTest.java
    {
        "file": "fhir-server-test/src/test/java/org/linuxforhealth/fhir/server/test/SearchTest.java",
        "replacements": [
            # Remove recorder and asserter setter lines - replace with participant entries
            ('.recorder(Reference.builder().reference(string("Practitioner/" + practitionerId2)).build())',
             '.participant(AllergyIntolerance.Participant.builder().function(CodeableConcept.builder().coding(Coding.builder().system(org.linuxforhealth.fhir.model.type.Uri.of("http://terminology.hl7.org/CodeSystem/provenance-participant-type")).code(Code.of("author")).build()).build()).actor(Reference.builder().reference(string("Practitioner/" + practitionerId2)).build()).build())'),
            ('.asserter(Reference.builder().reference(string("PractitionerRole/" + practitionerRoleId)).build())',
             '.participant(AllergyIntolerance.Participant.builder().function(CodeableConcept.builder().coding(Coding.builder().system(org.linuxforhealth.fhir.model.type.Uri.of("http://terminology.hl7.org/CodeSystem/provenance-participant-type")).code(Code.of("informant")).build()).build()).actor(Reference.builder().reference(string("PractitionerRole/" + practitionerRoleId)).build()).build())'),
        ]
    },
    # Base64BinaryTest.java - AuditEvent.type(Coding) -> category(CodeableConcept) or code(CodeableConcept)
    {
        "file": "fhir-server-test/src/test/java/org/linuxforhealth/fhir/server/test/Base64BinaryTest.java",
        "replacements": [
            ('.type(Coding.builder().code(Code.of("99")).build())',
             '.code(CodeableConcept.builder().coding(Coding.builder().code(Code.of("99")).build()).build())'),
        ]
    },
    # CarinBlueButtonV100Test.java - Coverage.payor -> insurer
    {
        "file": "fhir-server-test/src/test/java/org/linuxforhealth/fhir/server/test/profiles/CarinBlueButtonV100Test.java",
        "replacements": [
            ('.payor(Arrays.asList(org45ref))', '.insurer(org45ref)'),
        ]
    },
    # CarinBlueButtonV110Test.java - Coverage.payor -> insurer
    {
        "file": "fhir-server-test/src/test/java/org/linuxforhealth/fhir/server/test/profiles/CarinBlueButtonV110Test.java",
        "replacements": [
            ('.payor(Arrays.asList(org45ref))', '.insurer(org45ref)'),
        ]
    },
]

import os
base = "/Users/akhilkolli/Downloads/FHIR-main"

for fix in fixes:
    path = os.path.join(base, fix["file"])
    with open(path, "r") as f:
        content = f.read()
    changed = False
    for old, new in fix["replacements"]:
        if old in content:
            content = content.replace(old, new)
            print(f"Fixed: {old[:60]} in {fix['file']}")
            changed = True
        else:
            print(f"NOT FOUND: {old[:60]} in {fix['file']}")
    if changed:
        with open(path, "w") as f:
            f.write(content)
        print(f"  -> Saved {fix['file']}")
