#!/usr/bin/env python3
import re

filepath = "fhir-server/src/test/java/org/linuxforhealth/fhir/server/util/FHIRRestHelperTest.java"

with open(filepath, 'r') as f:
    content = f.read()

print(f"Garbled lines starting with 'string': {len(re.findall(r'^string', content, re.MULTILINE))}")

# Pattern 1: garbled multiple-reason encounter block
# .reason(Encounter.Reason.builder()
# string("VAL1"))
#                     .build(),
#                     Reference.builder()
#                     .reference(string("VAL2")                    .value(CodeableReference.builder()
# string("VAL1"))
# ...
# This was originally TWO separate .reasonReference(ref1, ref2) calls.
# Actually the original had two separate reasons: reasonReference pointing to urn:2 and urn:5.
# After R5 migration: two separate .reason() items or one .reason() with multiple values?
# Looking at the getter: getReason().get(0) and getReason().get(1) in lines 1234-1235
# So there should be two .reason() calls

# The garbled block at line 1099-1127:
# .reason(Encounter.Reason.builder()
# string("urn:2"))       <- urn:2 reference
#                     .build(),  <- these are extra Reference builders from original multi-value reasonReference
#                     Reference.builder()
#                     .reference(string("urn:5")  <- urn:5 reference
# Then the value/reference structure is interleaved

# The correct R5 form for TWO reasonReferences (urn:2 and urn:5) would be:
# .reason(Encounter.Reason.builder()
#     .value(CodeableReference.builder()
#         .reference(Reference.builder()
#             .reference(string("urn:2"))
#             .build())
#         .build())
#     .build())
# .reason(Encounter.Reason.builder()
#     .value(CodeableReference.builder()
#         .reference(Reference.builder()
#             .reference(string("urn:5"))
#             .build())
#         .build())
#     .build())

# Let me identify and replace those specific blocks.
# Pattern: 
# .reason(Encounter.Reason.builder()\nstring("X"))\n...lots of garble...\n string("X")                    .build())
# And for condition evidence:
# .evidence(CodeableReference.builder()\nstring("Y")                    .reference(...

# Rather than regex, let's just replace the specific garbled text directly:

# ===== BLOCK 1: testTransactionBundlePostWithMultipleDependency Encounter (lines ~1099-1128) =====
# Encounter with reasonRef to urn:2 and urn:5
old_block1 = '''.reason(Encounter.Reason.builder()
string("urn:2"))
                    .build(),
                    Reference.builder()
                    .reference(string("urn:5")                    .value(CodeableReference.builder()
string("urn:2"))
                    .build(),
                    Reference.builder()
                    .reference(string("urn:5")                        .reference(Reference.builder()
string("urn:2"))
                    .build(),
                    Reference.builder()
                    .reference(string("urn:5")                            .reference()
string("urn:2"))
                    .build(),
                    Reference.builder()
                    .reference(string("urn:5")                            .build())
string("urn:2"))
                    .build(),
                    Reference.builder()
                    .reference(string("urn:5")                        .build())
string("urn:2"))
                    .build(),
                    Reference.builder()
                    .reference(string("urn:5")                    .build())'''

new_block1 = '''.reason(Encounter.Reason.builder()
                    .value(CodeableReference.builder()
                        .reference(Reference.builder()
                            .reference(string("urn:2"))
                            .build())
                        .build())
                    .build())
                .reason(Encounter.Reason.builder()
                    .value(CodeableReference.builder()
                        .reference(Reference.builder()
                            .reference(string("urn:5"))
                            .build())
                        .build())
                    .build())'''

count1 = content.count(old_block1)
print(f"Block1 occurrences: {count1}")
if count1 > 0:
    content = content.replace(old_block1, new_block1)

# ===== BLOCK 2: Condition evidence blocks =====
# .evidence(CodeableReference.builder()
# string("urn:2")                    .reference(Reference.builder()
# string("urn:2")                        .reference()
# string("urn:2")                        .build())
# string("urn:2")                    .build())
old_evidence = '''.evidence(CodeableReference.builder()
string("urn:2")                    .reference(Reference.builder()
string("urn:2")                        .reference()
string("urn:2")                        .build())
string("urn:2")                    .build())'''

new_evidence = '''.evidence(CodeableReference.builder()
                    .reference(Reference.builder()
                        .reference(string("urn:5"))
                        .build())
                    .build())'''

count2 = content.count(old_evidence)
print(f"Evidence block occurrences: {count2}")
if count2 > 0:
    content = content.replace(old_evidence, new_evidence)

# Check if any garbled lines remain
remaining = len(re.findall(r'^string', content, re.MULTILINE))
print(f"Remaining garbled lines: {remaining}")

if remaining > 0:
    # Show them
    for m in re.finditer(r'^string[^\n]*', content, re.MULTILINE):
        line_num = content[:m.start()].count('\n') + 1
        print(f"  Line {line_num}: {m.group()[:80]}")

with open(filepath, 'w') as f:
    f.write(content)

print("Done.")
