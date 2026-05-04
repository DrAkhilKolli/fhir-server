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

    # Fix: Encounter.reason(CodeableReference...) 
    # The current form is .reason(CodeableReference.builder()\n indent.reference(Reference.builder()...
    # Needs to be: .reason(Encounter.Reason.builder().value(CodeableReference.builder().reference(...).build()).build())
    def fix_enc_reason(m):
        indent = m.group(1)
        ref_val = m.group(2).strip()
        return (f'.reason(Encounter.Reason.builder()\n'
                f'{indent}    .value(CodeableReference.builder()\n'
                f'{indent}        .reference(Reference.builder()\n'
                f'{indent}            .reference({ref_val})\n'
                f'{indent}            .build())\n'
                f'{indent}        .build())\n'
                f'{indent}    .build())')
    
    # Pattern: .reason(CodeableReference.builder()\nINDENT.reference(Reference.builder()\nINDENT    .reference(VAL)\nINDENT    .build())\nINDENT.build())
    # This is the pattern produced by fix_reasons.py for Encounter instances 
    content = re.sub(
        r'\.reason\(CodeableReference\.builder\(\)\n(\s+)\.reference\(Reference\.builder\(\)\n\s+\.reference\(([^\n]+)\)\n\s+\.build\(\)\)\n\s+\.build\(\)\)',
        fix_enc_reason,
        content
    )

    # Also fix clazz(Coding) patterns that weren't caught (different indentation/format)
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

    fname = filepath.split('/')[-1]
    remaining_cr_reason = len(re.findall(r'\.reason\(CodeableReference\.builder', content))
    remaining_clazz = len(re.findall(r'\.clazz\(Coding\.builder', content))
    print(f"{fname}: reason(CodeableReference)={remaining_cr_reason}, clazz(Coding)={remaining_clazz}")

    with open(filepath, 'w') as f:
        f.write(content)

for f in files:
    fix_file(f)
print("Done.")
