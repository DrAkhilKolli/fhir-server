#!/usr/bin/env python3
import re

filepath = "fhir-server/src/test/java/org/linuxforhealth/fhir/server/util/FHIRRestHelperTest.java"

with open(filepath, 'r') as f:
    content = f.read()

# 1. Fix Encounter.clazz(Coding) → clazz(List.of(CodeableConcept.builder().coding(Coding).build()))
# Pattern: .clazz(Coding.builder()\n    .code(Code.of("AMB"))\n    .build())
def fix_clazz(m):
    return '''.clazz(java.util.List.of(CodeableConcept.builder()
                    .coding(Coding.builder()
                        .code(Code.of("AMB"))
                        .build())
                    .build()))'''

content = re.sub(
    r'\.clazz\(Coding\.builder\(\)\s*\n\s*\.code\(Code\.of\("AMB"\)\)\s*\n\s*\.build\(\)\)',
    fix_clazz,
    content
)

count_clazz = len(re.findall(r'\.clazz\(Coding\.builder', content))
print(f"Remaining .clazz(Coding: {count_clazz}")

# 2. Fix Procedure.reason(Encounter.Reason.builder()...) → reason(CodeableReference.builder()...)
# Pattern: .reason(Encounter.Reason.builder()\n    .value(CodeableReference.builder()\n        .reference(Reference.builder()\n            .reference(REF)\n...
def fix_procedure_reason(m):
    ref = m.group(1).strip()
    return (f'.reason(CodeableReference.builder()\n'
            f'                    .reference(Reference.builder()\n'
            f'                        .reference({ref})\n'
            f'                        .build())\n'
            f'                    .build())')

content = re.sub(
    r'\.reason\(Encounter\.Reason\.builder\(\)\n\s+\.value\(CodeableReference\.builder\(\)\n\s+\.reference\(Reference\.builder\(\)\n\s+\.reference\(([^)]+)\)\n\s+\.build\(\)\)\n\s+\.build\(\)\)\n\s+\.build\(\)\)',
    fix_procedure_reason,
    content
)

count_enc_reason = len(re.findall(r'\.reason\(Encounter\.Reason\.builder', content))
print(f"Remaining Procedure.reason(Encounter.Reason: {count_enc_reason}")
# remaining .reason(Encounter.Reason - these should be for Encounter itself which is fine
if count_enc_reason > 0:
    for m in re.finditer(r'\.reason\(Encounter\.Reason\.builder', content):
        line = content[:m.start()].count('\n') + 1
        print(f"  line {line}")

# Check CodeableConcept import
if 'import org.linuxforhealth.fhir.model.type.CodeableConcept;' not in content:
    content = content.replace(
        'import org.linuxforhealth.fhir.model.resource.Condition;',
        'import org.linuxforhealth.fhir.model.resource.Condition;\nimport org.linuxforhealth.fhir.model.type.CodeableConcept;'
    )

with open(filepath, 'w') as f:
    f.write(content)
print("Done.")
