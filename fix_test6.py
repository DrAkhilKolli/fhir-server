#!/usr/bin/env python3
filepath = "fhir-server/src/test/java/org/linuxforhealth/fhir/server/util/FHIRRestHelperTest.java"

with open(filepath, 'r') as f:
    content = f.read()

def make_reason_block(ref1, ref2):
    """Generate two .reason() blocks for two references"""
    return f'''.reason(Encounter.Reason.builder()
                    .value(CodeableReference.builder()
                        .reference(Reference.builder()
                            .reference(string("{ref1}"))
                            .build())
                        .build())
                    .build())
                .reason(Encounter.Reason.builder()
                    .value(CodeableReference.builder()
                        .reference(Reference.builder()
                            .reference(string("{ref2}"))
                            .build())
                        .build())
                    .build())'''

def make_evidence_block(ref_val):
    """Generate a .evidence(CodeableReference...) block"""
    return f'''.evidence(CodeableReference.builder()
                    .reference(Reference.builder()
                        .reference(string("{ref_val}"))
                        .build())
                    .build())'''

def garbled_reason(ref1, ref2):
    return f'''.reason(Encounter.Reason.builder()
string("{ref1}"))
                    .build(),
                    Reference.builder()
                    .reference(string("{ref2}")                    .value(CodeableReference.builder()
string("{ref1}"))
                    .build(),
                    Reference.builder()
                    .reference(string("{ref2}")                        .reference(Reference.builder()
string("{ref1}"))
                    .build(),
                    Reference.builder()
                    .reference(string("{ref2}")                            .reference()
string("{ref1}"))
                    .build(),
                    Reference.builder()
                    .reference(string("{ref2}")                            .build())
string("{ref1}"))
                    .build(),
                    Reference.builder()
                    .reference(string("{ref2}")                        .build())
string("{ref1}"))
                    .build(),
                    Reference.builder()
                    .reference(string("{ref2}")                    .build())'''

def garbled_evidence(ref_val):
    return f'''.evidence(CodeableReference.builder()
string("{ref_val}")                    .reference(Reference.builder()
string("{ref_val}")                        .reference()
string("{ref_val}")                        .build())
string("{ref_val}")                    .build())'''

# All replacements
replacements = [
    # Test 1: urn:2 / urn:5 (already done in fix_test5, but may be remaining)
    (garbled_reason("urn:2", "urn:5"), make_reason_block("urn:2", "urn:5")),
    (garbled_evidence("urn:2"), make_evidence_block("urn:5")),
    # Test 2: Procedure/1 / Condition/1
    (garbled_reason("Procedure/1", "Condition/1"), make_reason_block("Procedure/1", "Condition/1")),
    # Test 3: resource:1 / resource:4
    (garbled_reason("resource:1", "resource:4"), make_reason_block("resource:1", "resource:4")),
    (garbled_evidence("resource:1"), make_evidence_block("resource:4")),
]

for old, new in replacements:
    count = content.count(old)
    print(f"Replacing ({count}x): {old[:60].strip()}")
    content = content.replace(old, new)

import re
remaining = len(re.findall(r'^string', content, re.MULTILINE))
print(f"\nRemaining garbled lines: {remaining}")
if remaining > 0:
    for m in re.finditer(r'^string[^\n]*', content, re.MULTILINE):
        line_num = content[:m.start()].count('\n') + 1
        print(f"  Line {line_num}: {m.group()[:80]}")

with open(filepath, 'w') as f:
    f.write(content)
print("Done.")
