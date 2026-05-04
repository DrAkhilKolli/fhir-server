#!/usr/bin/env python3
import re

filepath = "fhir-server/src/test/java/org/linuxforhealth/fhir/server/util/FHIRRestHelperTest.java"

with open(filepath, 'r') as f:
    content = f.read()

# Fix EncounterStatus.FINISHED → COMPLETED  
content = content.replace('EncounterStatus.FINISHED', 'EncounterStatus.COMPLETED')

# Fix the malformed .reason(Encounter.Reason.builder() blocks from previous run
# Pattern: .reason(Encounter.Reason.builder()\nVALUE    .value(CodeableReference.builder()\nVALUE    .reference(Reference.builder()...
# First, undo the previous damage and re-apply correctly

# Repair the garbled patterns first
content = re.sub(
    r'\.reason\(Encounter\.Reason\.builder\(\)\n.*?\s+\.value\(CodeableReference\.builder\(\)\n.*?\s+\.reference\(Reference\.builder\(\)\n.*?\s+\.reference\(\)\n.*?\s+\.build\(\)\)\n.*?\s+\.build\(\)\)\n.*?\s+\.build\(\)\)',
    None,  # placeholder - we'll use a function
    content,
    flags=re.DOTALL
)

with open(filepath, 'r') as f:
    content = f.read()

# Count garbled blocks
garbled_count = content.count('.reason(Encounter.Reason.builder()\n')
print(f"Garbled reason blocks: {garbled_count}")
print(f"reasonReference count: {content.count('reasonReference')}")

# The file has garbled content. We need to find and fix each garbled block.
# Pattern from output:
# .reason(Encounter.Reason.builder()
# string("urn:X")                    .value(CodeableReference.builder()
# string("urn:X")                        .reference(Reference.builder()
# string("urn:X")                            .reference()
# string("urn:X")                            .build())
# string("urn:X")                        .build())
# string("urn:X")                    .build())

def fix_garbled_reason(m):
    ref_val = m.group(1)  # the reference value like string("urn:2")
    indent = '                '
    return (f'.reason(Encounter.Reason.builder()\n'
            f'{indent}    .value(CodeableReference.builder()\n'
            f'{indent}        .reference(Reference.builder()\n'
            f'{indent}            .reference({ref_val})\n'
            f'{indent}            .build())\n'
            f'{indent}        .build())\n'
            f'{indent}    .build())')

content = re.sub(
    r'\.reason\(Encounter\.Reason\.builder\(\)\n'
    r'([^\n]+)\s+\.value\(CodeableReference\.builder\(\)\n'
    r'[^\n]+\s+\.reference\(Reference\.builder\(\)\n'
    r'[^\n]+\s+\.reference\(\)\n'
    r'[^\n]+\s+\.build\(\)\)\n'
    r'[^\n]+\s+\.build\(\)\)\n'
    r'[^\n]+\s+\.build\(\)\)',
    fix_garbled_reason,
    content
)

garbled_count2 = content.count('.reason(Encounter.Reason.builder()\n')
print(f"After fix garbled reason blocks: {garbled_count2}")
print(f"reasonReference count: {content.count('reasonReference')}")

# Now fix remaining .reasonReference(Reference.builder()\n    .reference(VALUE)\n    .build())
def fix_reasonReference(m):
    ref_val = m.group(1).strip()
    indent = '                '
    return (f'.reason(Encounter.Reason.builder()\n'
            f'{indent}    .value(CodeableReference.builder()\n'
            f'{indent}        .reference(Reference.builder()\n'
            f'{indent}            .reference({ref_val})\n'
            f'{indent}            .build())\n'
            f'{indent}        .build())\n'
            f'{indent}    .build())')

content = re.sub(
    r'\.reasonReference\(Reference\.builder\(\)\n\s*\.reference\((.*?)\)\s*\.build\(\)\)',
    fix_reasonReference,
    content,
    flags=re.DOTALL
)

# Fix Condition.Evidence.builder().detail(Reference.builder().reference(VALUE).build()).build()
# → CodeableReference.builder().reference(Reference.builder().reference(VALUE).build()).build()
def fix_condition_evidence(m):
    ref_val = m.group(1).strip()
    indent = '                '
    return (f'.evidence(CodeableReference.builder()\n'
            f'{indent}    .reference(Reference.builder()\n'
            f'{indent}        .reference({ref_val})\n'
            f'{indent}        .build())\n'
            f'{indent}    .build())')

content = re.sub(
    r'\.evidence\(Condition\.Evidence\.builder\(\)\n\s*\.detail\(Reference\.builder\(\)\s*\.reference\((.*?)\)\s*\.build\(\)\)\s*\.build\(\)\)',
    fix_condition_evidence,
    content,
    flags=re.DOTALL
)

# Fix getters: returnedX.getReasonReference()...
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

with open(filepath, 'w') as f:
    f.write(content)

print("Done.")
