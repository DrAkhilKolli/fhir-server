#!/usr/bin/env python3
import re

files = [
    "fhir-server/src/test/java/org/linuxforhealth/fhir/server/util/FHIRRestHelperTest.java",
    "fhir-server/src/test/java/org/linuxforhealth/fhir/server/test/ProfileValidationConfigTest.java",
    "fhir-server/src/test/java/org/linuxforhealth/fhir/server/test/InteractionValidationConfigTest.java",
]

def fix_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()
    
    # 1. Fix .reasonReference(Reference.builder()\n    .reference(VAL)\n    .build())
    # VAL can contain parens like string("urn:2") - use [^\n]+ instead of [^)]+
    def fix_encounter_reason(m):
        indent = m.group(1)
        ref_val = m.group(2).strip()
        return (f'.reason(Encounter.Reason.builder()\n'
                f'{indent}    .value(CodeableReference.builder()\n'
                f'{indent}        .reference(Reference.builder()\n'
                f'{indent}            .reference({ref_val})\n'
                f'{indent}            .build())\n'
                f'{indent}        .build())\n'
                f'{indent}    .build())')
    
    content = re.sub(
        r'\.reasonReference\(Reference\.builder\(\)\n(\s+)\.reference\(([^\n]+)\)\n\s+\.build\(\)\)',
        fix_encounter_reason,
        content
    )
    
    # 2. Fix Procedure.reason(Encounter.Reason.builder()...) → reason(CodeableReference.builder()...)
    def fix_procedure_reason(m):
        indent = m.group(1)
        ref_val = m.group(2).strip()
        return (f'.reason(CodeableReference.builder()\n'
                f'{indent}.reference(Reference.builder()\n'
                f'{indent}    .reference({ref_val})\n'
                f'{indent}    .build())\n'
                f'{indent}.build())')
    
    content = re.sub(
        r'\.reason\(Encounter\.Reason\.builder\(\)\n(\s+)\.value\(CodeableReference\.builder\(\)\n\s+\.reference\(Reference\.builder\(\)\n\s+\.reference\(([^\n]+)\)\n\s+\.build\(\)\)\n\s+\.build\(\)\)\n\s+\.build\(\)\)',
        fix_procedure_reason,
        content
    )
    
    # 3. Add CodeableReference import if missing
    if 'import org.linuxforhealth.fhir.model.type.CodeableReference;' not in content:
        content = re.sub(
            r'(import org\.linuxforhealth\.fhir\.model\.type\.CodeableConcept;)',
            r'\1\nimport org.linuxforhealth.fhir.model.type.CodeableReference;',
            content
        )
        if 'import org.linuxforhealth.fhir.model.type.CodeableReference;' not in content:
            # Fallback: add after last fhir import
            content = re.sub(
                r'(import org\.linuxforhealth\.fhir\.model\.resource\.Encounter;)',
                r'import org.linuxforhealth.fhir.model.type.CodeableReference;\n\1',
                content
            )
    
    fname = filepath.split('/')[-1]
    remaining_rr = content.count('.reasonReference(')
    remaining_enc_reason = len(re.findall(r'\.reason\(Encounter\.Reason\.builder', content))
    print(f"{fname}: reasonReference={remaining_rr}, Procedure.reason(Encounter.Reason)={remaining_enc_reason}")
    
    with open(filepath, 'w') as f:
        f.write(content)

for f in files:
    fix_file(f)
print("Done.")
