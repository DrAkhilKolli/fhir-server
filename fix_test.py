#!/usr/bin/env python3
import re
import sys

filepath = "fhir-server/src/test/java/org/linuxforhealth/fhir/server/util/FHIRRestHelperTest.java"

with open(filepath, 'r') as f:
    content = f.read()

orig = content

# Fix builder: .reasonReference(Reference.builder()\n    .reference(...)\n    .build())
# → .reason(Encounter.Reason.builder().value(CodeableReference.builder().reference(Reference.builder().reference(...).build()).build()).build())
def fix_encounter_reasonReference(m):
    ref_val = m.group(1).strip()
    return (f'.reason(Encounter.Reason.builder()\n'
            f'                    .value(CodeableReference.builder()\n'
            f'                        .reference(Reference.builder()\n'
            f'                            .reference({ref_val})\n'
            f'                            .build())\n'
            f'                        .build())\n'
            f'                    .build())')

# Encounter.reasonReference pattern
content = re.sub(
    r'\.reasonReference\(Reference\.builder\(\)\s*\.reference\(([^)]+)\)\s*\.build\(\)\)',
    fix_encounter_reasonReference,
    content,
    flags=re.DOTALL
)

# Fix Condition.Evidence.builder().detail(Reference.builder().reference(...).build()).build()
# → CodeableReference.builder().reference(Reference.builder().reference(...).build()).build()
def fix_condition_evidence(m):
    ref_val = m.group(1).strip()
    return (f'.evidence(CodeableReference.builder()\n'
            f'                    .reference(Reference.builder()\n'
            f'                        .reference({ref_val})\n'
            f'                        .build())\n'
            f'                    .build())')

content = re.sub(
    r'\.evidence\(Condition\.Evidence\.builder\(\)\s*\.detail\(Reference\.builder\(\)\s*\.reference\(([^)]+)\)\s*\.build\(\)\)\s*\.build\(\)\)',
    fix_condition_evidence,
    content,
    flags=re.DOTALL
)

# Fix getter: returnedEncounter.getReasonReference().get(N).getReference().getValue()
# → returnedEncounter.getReason().get(N).getValue().get(0).getReference().getReference().getValue()
content = re.sub(
    r'(returnedEncounter)\.getReasonReference\(\)\.get\((\d+)\)\.getReference\(\)\.getValue\(\)',
    r'\1.getReason().get(\2).getValue().get(0).getReference().getReference().getValue()',
    content
)

# Fix getter: returnedProcedure.getReasonReference().get(N).getReference().getValue()
# → returnedProcedure.getReason().get(N).getReference().getReference().getValue()
content = re.sub(
    r'(returnedProcedure)\.getReasonReference\(\)\.get\((\d+)\)\.getReference\(\)\.getValue\(\)',
    r'\1.getReason().get(\2).getReference().getReference().getValue()',
    content
)

# Fix assertion: assertEquals("...", returnedEncounter.getReasonReference().get(N).getReference().getValue())
content = re.sub(
    r'(returnedEncounter)\.getReasonReference\(\)\.get\((\d+)\)\.getReference\(\)\.getValue\(\)',
    r'\1.getReason().get(\2).getValue().get(0).getReference().getReference().getValue()',
    content
)

# Fix getter: returnedCondition.getEvidence().get(0).getDetail().get(0).getReference().getValue()
# → returnedCondition.getEvidence().get(0).getReference().getReference().getValue()
content = re.sub(
    r'(returnedCondition)\.getEvidence\(\)\.get\((\d+)\)\.getDetail\(\)\.get\((\d+)\)\.getReference\(\)\.getValue\(\)',
    r'\1.getEvidence().get(\2).getReference().getReference().getValue()',
    content
)

# Add CodeableReference import if not present
if 'import org.linuxforhealth.fhir.model.type.CodeableReference;' not in content:
    content = content.replace(
        'import org.linuxforhealth.fhir.model.resource.Condition;',
        'import org.linuxforhealth.fhir.model.resource.Condition;\nimport org.linuxforhealth.fhir.model.type.CodeableReference;'
    )

# Remove Condition.Evidence import if present (it's an inner class, accessed via Condition.Evidence)
# (no explicit import needed as it's accessed as Condition.Evidence which is already imported via Condition)

changes = sum(1 for a, b in zip(orig.splitlines(), content.splitlines()) if a != b)
print(f"Lines changed: {changes}")

with open(filepath, 'w') as f:
    f.write(content)

print("Done.")
