#!/usr/bin/env python3
import re

filepath = "fhir-server/src/test/java/org/linuxforhealth/fhir/server/util/FHIRRestHelperTest.java"

with open(filepath, 'r') as f:
    content = f.read()

# Fix EncounterStatus.FINISHED → COMPLETED
content = content.replace('EncounterStatus.FINISHED', 'EncounterStatus.COMPLETED')

# Fix builder: .reasonReference(Reference.builder()\n    .reference(VALUE)\n    .build())
# → .reason(Encounter.Reason.builder()\n    .value(CodeableReference.builder()\n        .reference(Reference.builder()\n            .reference(VALUE)\n            .build())\n        .build())\n    .build())
def fix_encounter_reasonReference(m):
    ref_val = m.group(1).strip()
    indent = m.group(2)
    return (f'.reason(Encounter.Reason.builder()\n'
            f'{indent}                    .value(CodeableReference.builder()\n'
            f'{indent}                        .reference(Reference.builder()\n'
            f'{indent}                            .reference({ref_val})\n'
            f'{indent}                            .build())\n'
            f'{indent}                        .build())\n'
            f'{indent}                    .build())')

content = re.sub(
    r'\.reasonReference\(Reference\.builder\(\)\n([ \t]*)\.reference\((.*?)\)\s*\.build\(\)\)',
    fix_encounter_reasonReference,
    content,
    flags=re.DOTALL
)

# Fix Condition.Evidence.builder().detail(Reference.builder().reference(VALUE).build()).build()
# → CodeableReference.builder().reference(Reference.builder().reference(VALUE).build()).build()
def fix_condition_evidence(m):
    ref_val = m.group(1).strip()
    indent = m.group(2)
    return (f'.evidence(CodeableReference.builder()\n'
            f'{indent}                    .reference(Reference.builder()\n'
            f'{indent}                        .reference({ref_val})\n'
            f'{indent}                        .build())\n'
            f'{indent}                    .build())')

content = re.sub(
    r'\.evidence\(Condition\.Evidence\.builder\(\)\n([ \t]*)\.detail\(Reference\.builder\(\)\s*\.reference\((.*?)\)\s*\.build\(\)\)\s*\.build\(\)\)',
    fix_condition_evidence,
    content,
    flags=re.DOTALL
)

# Fix getter: X.getReasonReference().get(N).getReference().getValue()
# For Encounter:
content = re.sub(
    r'(returnedEncounter)\.getReasonReference\(\)\.get\((\d+)\)\.getReference\(\)\.getValue\(\)',
    r'\1.getReason().get(\2).getValue().get(0).getReference().getReference().getValue()',
    content
)
# For asserting pattern: assertEquals("...", returnedEncounter.getReasonReference()...)
content = re.sub(
    r'(returnedEncounter)\.getReasonReference\(\)\.get\((\d+)\)\.getReference\(\)\.getValue\(\)',
    r'\1.getReason().get(\2).getValue().get(0).getReference().getReference().getValue()',
    content
)

# For Procedure:
content = re.sub(
    r'(returnedProcedure)\.getReasonReference\(\)\.get\((\d+)\)\.getReference\(\)\.getValue\(\)',
    r'\1.getReason().get(\2).getReference().getReference().getValue()',
    content
)

# Fix Condition evidence getter:
# getEvidence().get(N).getDetail().get(N).getReference().getValue()
# → getEvidence().get(N).getReference().getReference().getValue()
content = re.sub(
    r'(returnedCondition)\.getEvidence\(\)\.get\((\d+)\)\.getDetail\(\)\.get\(\d+\)\.getReference\(\)\.getValue\(\)',
    r'\1.getEvidence().get(\2).getReference().getReference().getValue()',
    content
)

# Also handle inline assertion patterns
content = re.sub(
    r'"([^"]+)",\s*(returnedEncounter)\.getReasonReference\(\)\.get\((\d+)\)\.getReference\(\)\.getValue\(\)',
    r'"\1", \2.getReason().get(\3).getValue().get(0).getReference().getReference().getValue()',
    content
)
content = re.sub(
    r'"([^"]+)",\s*(returnedProcedure)\.getReasonReference\(\)\.get\((\d+)\)\.getReference\(\)\.getValue\(\)',
    r'"\1", \2.getReason().get(\3).getReference().getReference().getValue()',
    content
)

# Add CodeableReference import if not present
if 'import org.linuxforhealth.fhir.model.type.CodeableReference;' not in content:
    content = content.replace(
        'import org.linuxforhealth.fhir.model.resource.Condition;',
        'import org.linuxforhealth.fhir.model.resource.Condition;\nimport org.linuxforhealth.fhir.model.type.CodeableReference;'
    )

# Count remaining issues
print(f"Remaining 'reasonReference': {content.count('reasonReference')}")
print(f"Remaining 'getReasonReference': {content.count('getReasonReference')}")
print(f"Remaining 'Condition.Evidence': {content.count('Condition.Evidence')}")
print(f"Remaining 'getDetail': {content.count('getDetail')}")
print(f"Remaining 'FINISHED': {content.count('EncounterStatus.FINISHED')}")

with open(filepath, 'w') as f:
    f.write(content)

print("Done.")
