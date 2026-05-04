#!/usr/bin/env python3
import re

filepath = "fhir-server/src/test/java/org/linuxforhealth/fhir/server/test/InteractionValidationConfigTest.java"

with open(filepath, 'r') as f:
    content = f.read()

# Fix .clazz(Coding.builder()\n    .code(...)\n    .system(...)\n    .build())
def fix_clazz_with_system(m):
    code_val = m.group(1).strip()
    sys_val = m.group(2).strip()
    return (f'.clazz(java.util.List.of(CodeableConcept.builder()\n'
            f'                    .coding(Coding.builder()\n'
            f'                        .code({code_val})\n'
            f'                        .system({sys_val})\n'
            f'                        .build())\n'
            f'                    .build()))')

content = re.sub(
    r'\.clazz\(Coding\.builder\(\)\n\s+\.code\(([^\n]+)\)\n\s+\.system\(([^\n]+)\)\n\s+\.build\(\)\)',
    fix_clazz_with_system,
    content
)

# Also fix without system (2-line version)
def fix_clazz_no_system(m):
    code_val = m.group(1).strip()
    return (f'.clazz(java.util.List.of(CodeableConcept.builder()\n'
            f'                    .coding(Coding.builder()\n'
            f'                        .code({code_val})\n'
            f'                        .build())\n'
            f'                    .build()))')

content = re.sub(
    r'\.clazz\(Coding\.builder\(\)\n\s+\.code\(([^\n]+)\)\n\s+\.build\(\)\)',
    fix_clazz_no_system,
    content
)

remaining = len(re.findall(r'\.clazz\(Coding\.builder', content))
print(f"Remaining clazz(Coding): {remaining}")

with open(filepath, 'w') as f:
    f.write(content)
print("Done.")
