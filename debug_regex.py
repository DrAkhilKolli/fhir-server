#!/usr/bin/env python3
import re

filepath = "fhir-server/src/test/java/org/linuxforhealth/fhir/server/test/ProfileValidationConfigTest.java"
with open(filepath, 'r') as f:
    content = f.read()

# Find the first .reasonReference( and show context
idx = content.find('.reasonReference(')
print(f"Found at index: {idx}")
snippet = content[idx:idx+200]
print(repr(snippet))
print()

# Test our regex
pattern = r'\.reasonReference\(\s*Reference\.builder\(\)\s*\n(\s*)\.reference\(([^)]+)\)\s*\n\s*\.build\(\)\)'
m = re.search(pattern, content)
if m:
    print(f"Pattern matched!")
else:
    print("Pattern did NOT match")
    # Try to see what's around the text
    # Look for .reasonReference pattern manually
    m2 = re.search(r'\.reasonReference\(Reference\.builder\(\)', content)
    if m2:
        idx2 = m2.start()
        print(f"Found simpler pattern at index {idx2}")
        print(repr(content[idx2:idx2+150]))
