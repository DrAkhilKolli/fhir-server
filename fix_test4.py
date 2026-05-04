#!/usr/bin/env python3
import re

filepath = "fhir-server/src/test/java/org/linuxforhealth/fhir/server/util/FHIRRestHelperTest.java"

with open(filepath, 'r') as f:
    content = f.read()

print(f"File size: {len(content)}")

# The garbled pattern from od output is:
# .reason(Encounter.Reason.builder()
# string("VAL")                    .value(CodeableReference.builder()
# string("VAL")                        .reference(Reference.builder()
# string("VAL")                            .reference()
# string("VAL")                            .build())
# string("VAL")                        .build())
# string("VAL")                    .build())
# i.e. each newline is followed by string("VAL") then spaces then the java code

# Strategy: find these blocks by detecting .reason(Encounter.Reason.builder() followed by 
# a newline then string("...")
# and extract the VAL to reconstruct the correct Java

def fix_garbled_reason_block(m):
    val = m.group(1)  # e.g. "urn:2"
    ind = '                '
    return (f'.reason(Encounter.Reason.builder()\n'
            f'{ind}    .value(CodeableReference.builder()\n'
            f'{ind}        .reference(Reference.builder()\n'
            f'{ind}            .reference(string("{val}"))\n'
            f'{ind}            .build())\n'
            f'{ind}        .build())\n'
            f'{ind}    .build())')

# Pattern: .reason(Encounter.Reason.builder()\nstring("VAL")...
# The value appears after the first newline as string("VAL") 
pattern = (
    r'\.reason\(Encounter\.Reason\.builder\(\)\n'
    r'string\("([^"]+)"\)\s+\.value\(CodeableReference\.builder\(\)\n'
    r'string\("[^"]+"\)\s+\.reference\(Reference\.builder\(\)\n'
    r'string\("[^"]+"\)\s+\.reference\(\)\n'
    r'string\("[^"]+"\)\s+\.build\(\)\)\n'
    r'string\("[^"]+"\)\s+\.build\(\)\)\n'
    r'string\("[^"]+"\)\s+\.build\(\)\)'
)

count_before = len(re.findall(pattern, content))
print(f"Garbled reason blocks found: {count_before}")

content = re.sub(pattern, fix_garbled_reason_block, content)

count_after = len(re.findall(pattern, content))
print(f"Garbled reason blocks remaining: {count_after}")

# Now fix any remaining .reasonReference patterns (not yet converted)
count_rr = content.count('.reasonReference(')
print(f"Remaining .reasonReference() calls: {count_rr}")

def fix_reasonReference(m):
    leading_ws = m.group(1)
    val = m.group(2).strip()
    ind = leading_ws
    return (f'.reason(Encounter.Reason.builder()\n'
            f'{ind}    .value(CodeableReference.builder()\n'
            f'{ind}        .reference(Reference.builder()\n'
            f'{ind}            .reference({val})\n'
            f'{ind}            .build())\n'
            f'{ind}        .build())\n'
            f'{ind}    .build())')

content = re.sub(
    r'\.reasonReference\(\s*Reference\.builder\(\)\s*\n(\s*)\.reference\(([^)]+)\)\s*\.build\(\)\)',
    fix_reasonReference,
    content,
    flags=re.DOTALL
)

# Fix getters
content = re.sub(
    r'returnedEncounter\.getReasonReference\(\)\.get\((\d+)\)\.getReference\(\)\.getValue\(\)',
    r'returnedEncounter.getReason().get(\1).getValue().get(0).getReference().getReference().getValue()',
    content
)
content = re.sub(
    r'returnedProcedure\.getReasonReference\(\)\.get\((\d+)\)\.getReference\(\)\.getValue\(\)',
    r'returnedProcedure.getReason().get(\1).getReference().getReference().getValue()',
    content
)
content = re.sub(
    r'returnedCondition\.getEvidence\(\)\.get\((\d+)\)\.getDetail\(\)\.get\(\d+\)\.getReference\(\)\.getValue\(\)',
    r'returnedCondition.getEvidence().get(\1).getReference().getReference().getValue()',
    content
)

# Fix Condition.Evidence.builder()...detail(...) → CodeableReference.builder()...reference(...)
def fix_evidence(m):
    leading_ws = m.group(1)
    ref_content = m.group(2).strip()
    ind = leading_ws
    return (f'.evidence(CodeableReference.builder()\n'
            f'{ind}    .reference(Reference.builder()\n'
            f'{ind}        .reference({ref_content})\n'
            f'{ind}        .build())\n'
            f'{ind}    .build())')

content = re.sub(
    r'\.evidence\(\s*Condition\.Evidence\.builder\(\)\s*\n(\s*)\.detail\(\s*Reference\.builder\(\)\s*\.reference\(([^)]+)\)\s*\.build\(\)\)\s*\.build\(\)\)',
    fix_evidence,
    content,
    flags=re.DOTALL
)

# Add CodeableReference import if not present
if 'import org.linuxforhealth.fhir.model.type.CodeableReference;' not in content:
    content = content.replace(
        'import org.linuxforhealth.fhir.model.resource.Condition;',
        'import org.linuxforhealth.fhir.model.resource.Condition;\nimport org.linuxforhealth.fhir.model.type.CodeableReference;'
    )

print(f"\nFinal counts:")
print(f"reasonReference: {content.count('reasonReference')}")
print(f"getReasonReference: {content.count('getReasonReference')}")
print(f"Condition.Evidence: {content.count('Condition.Evidence')}")
print(f"EncounterStatus.FINISHED: {content.count('EncounterStatus.FINISHED')}")
print(f'garbled lines starting with string: {len(re.findall(r"^string", content, re.MULTILINE))}')

with open(filepath, 'w') as f:
    f.write(content)

print("Done.")
