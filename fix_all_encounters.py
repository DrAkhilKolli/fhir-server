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
    
    orig_len = len(content)
    
    # 1. FINISHED → COMPLETED
    content = content.replace('EncounterStatus.FINISHED', 'EncounterStatus.COMPLETED')
    
    # 2. .clazz(Coding.builder()\n    .code(Code.of("AMB"))\n    .build())
    #    → .clazz(java.util.List.of(CodeableConcept.builder().coding(...).build()))
    def fix_clazz(m):
        return '''.clazz(java.util.List.of(CodeableConcept.builder()
                    .coding(Coding.builder()
                        .code(Code.of("AMB"))
                        .build())
                    .build()))'''
    
    content = re.sub(
        r'\.clazz\(\s*Coding\.builder\(\)\s*\n\s*\.code\(Code\.of\("AMB"\)\)\s*\n\s*\.build\(\)\)',
        fix_clazz,
        content
    )
    
    # 3. Single .reasonReference(Reference.builder()\n    .reference(VAL)\n    .build())
    #    → .reason(Encounter.Reason.builder().value(CodeableReference.builder().reference(...).build()).build())
    def fix_encounter_reason(m):
        # capture indentation
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
        r'\.reasonReference\(\s*Reference\.builder\(\)\s*\n(\s*)\.reference\(([^)]+)\)\s*\n\s*\.build\(\)\)',
        fix_encounter_reason,
        content
    )
    
    # 4. Fix Procedure.reason(Encounter.Reason.builder()...) → reason(CodeableReference.builder()...)
    def fix_procedure_reason(m):
        indent = m.group(1)
        ref_val = m.group(2).strip()
        return (f'.reason(CodeableReference.builder()\n'
                f'{indent}    .reference(Reference.builder()\n'
                f'{indent}        .reference({ref_val})\n'
                f'{indent}        .build())\n'
                f'{indent}    .build())')
    
    # These are inside Procedure.builder() context; detect by checking for Encounter.Reason  
    # The Procedure reason uses Encounter.Reason which is wrong - fix those
    # Pattern: .reason(Encounter.Reason.builder()\n    .value(CodeableReference.builder()\n        .reference(Reference.builder()\n            .reference(VAL)
    content = re.sub(
        r'\.reason\(Encounter\.Reason\.builder\(\)\n(\s+)\.value\(CodeableReference\.builder\(\)\n\s+\.reference\(Reference\.builder\(\)\n\s+\.reference\(([^)]+)\)\n\s+\.build\(\)\)\n\s+\.build\(\)\)\n\s+\.build\(\)\)',
        fix_procedure_reason,
        content
    )
    
    # 5. Add imports if needed
    if 'import org.linuxforhealth.fhir.model.type.CodeableConcept;' not in content:
        # Find last import line ending with Condition or similar and add after
        content = re.sub(
            r'(import org\.linuxforhealth\.fhir\.model\.resource\.Condition;)',
            r'\1\nimport org.linuxforhealth.fhir.model.type.CodeableConcept;',
            content
        )
    
    if 'import org.linuxforhealth.fhir.model.type.CodeableReference;' not in content:
        # Add after CodeableConcept import or Condition import
        content = re.sub(
            r'(import org\.linuxforhealth\.fhir\.model\.type\.CodeableConcept;)',
            r'\1\nimport org.linuxforhealth.fhir.model.type.CodeableReference;',
            content
        )
    
    remaining_rr = content.count('.reasonReference(')
    remaining_finished = content.count('EncounterStatus.FINISHED')
    remaining_clazz = content.count('.clazz(Coding.builder()')
    remaining_enc_reason = len(re.findall(r'\.reason\(Encounter\.Reason\.builder', content))
    
    print(f"\n{filepath.split('/')[-1]}:")
    print(f"  reasonReference: {remaining_rr}")
    print(f"  FINISHED: {remaining_finished}")
    print(f"  clazz(Coding: {remaining_clazz}")
    print(f"  Procedure.reason(Encounter.Reason): {remaining_enc_reason}")
    
    with open(filepath, 'w') as f:
        f.write(content)

for f in files:
    fix_file(f)

print("\nDone.")
