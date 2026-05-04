#!/usr/bin/env python3
import re

files = [
    "fhir-server/src/test/java/org/linuxforhealth/fhir/server/util/FHIRRestHelperTest.java",
    "fhir-server/src/test/java/org/linuxforhealth/fhir/server/test/ProfileValidationConfigTest.java",
    "fhir-server/src/test/java/org/linuxforhealth/fhir/server/test/InteractionValidationConfigTest.java",
]

# Problem: Some Procedure.Builder instances have .reason(Encounter.Reason.builder()...) 
# which is wrong — Procedure needs CodeableReference, not Encounter.Reason
# We need to detect these by looking at the surrounding Procedure.builder() context.

def fix_file(filepath):
    with open(filepath, 'r') as f:
        lines = f.readlines()
    
    content = ''.join(lines)
    
    # Strategy: find all `.reason(Encounter.Reason.builder()...` blocks and determine
    # whether they're inside a Procedure.builder() or Encounter.builder() chain.
    # 
    # For each .reason(Encounter.Reason...) block, scan backwards to find the nearest 
    # Procedure.builder() or Encounter.builder() declaration.
    
    # Find all occurrences and process from end to start to preserve positions
    pattern = (
        r'\.reason\(Encounter\.Reason\.builder\(\)\n'
        r'(\s+)\.value\(CodeableReference\.builder\(\)\n'
        r'\s+\.reference\(Reference\.builder\(\)\n'
        r'\s+\.reference\(([^\n]+)\)\n'
        r'\s+\.build\(\)\)\n'
        r'\s+\.build\(\)\)\n'
        r'\s+\.build\(\)\)'
    )
    
    matches = list(re.finditer(pattern, content))
    print(f"{filepath.split('/')[-1]}: found {len(matches)} Encounter.Reason blocks")
    
    # For each match, find nearest resource builder declaration before it
    replacements = []
    for m in matches:
        # Look back from match start for either "Procedure.builder()" or "Encounter.builder()"
        preceding = content[:m.start()]
        # Find the last occurrence of either pattern
        enc_idx = preceding.rfind('Encounter.builder()')
        proc_idx = preceding.rfind('Procedure.builder()')
        
        if proc_idx > enc_idx:
            resource = 'Procedure'
        else:
            resource = 'Encounter'
        
        print(f"  At pos {m.start()}: belongs to {resource}")
        replacements.append((m.start(), m.end(), resource, m.group(1), m.group(2)))
    
    # Apply replacements from end to start
    for start, end, resource, indent, ref_val in reversed(replacements):
        if resource == 'Procedure':
            new_text = (f'.reason(CodeableReference.builder()\n'
                       f'{indent}.reference(Reference.builder()\n'
                       f'{indent}    .reference({ref_val.strip()})\n'
                       f'{indent}    .build())\n'
                       f'{indent}.build())')
        else:
            # Encounter - keep as Encounter.Reason
            new_text = content[start:end]  # unchanged
        
        content = content[:start] + new_text + content[end:]
    
    # Verify
    remaining = len(re.findall(r'\.reason\(Encounter\.Reason\.builder', content))
    print(f"  Remaining Encounter.Reason blocks: {remaining}")
    
    with open(filepath, 'w') as f:
        f.write(content)

for f in files:
    fix_file(f)
print("\nDone.")
