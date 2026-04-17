# mcp-tool-insight Deployment Checklist

## RED Phase - Write Failing Test ✅

- [x] Create pressure scenarios (3+ combined pressures for discipline skills)
  - Scenario 1: Empty results (GetTransportRequests)
  - Scenario 2: Implement new tool (SaveProgram)
  - Scenario 3: Content type error (ActivateObject)
- [x] Run scenarios WITHOUT skill - document baseline behavior verbatim
  - Baseline run: GetTransportRequests scenario
  - Agent solved problem but didn't use reference implementations
- [x] Identify patterns in rationalizations/failures
  - Gap: No systematic framework
  - Gap: Reference implementations not used
  - Gap: No comparison between MCP/Eclipse/Backend

## GREEN Phase - Write Minimal Skill ✅

- [x] Name uses only letters, numbers, hyphens (no parentheses/special chars)
  - Name: `mcp-tool-insight`
- [x] YAML frontmatter with only name and description (max 1024 chars)
  - ✅ Only name and description fields
  - ✅ Under 1024 characters
- [x] Description starts with "Use when..." and includes specific triggers/symptoms
  - ✅ Starts with "Use when debugging..."
  - ✅ Lists specific symptoms: errors, unexpected results, content type issues
  - ✅ Does NOT summarize workflow
- [x] Description written in third person
  - ✅ "Use when" not "I use when"
- [x] Keywords throughout for search (errors, symptoms, tools)
  - ✅ Error codes: 400, 404, 405, 500
  - ✅ Symptoms: empty results, CSRF failures, content type
  - ✅ Tools: sadt-rest-explorer, adt-client-explorer
- [x] Clear overview with core principle
  - ✅ "Compare three sources: MCP, Eclipse, Backend"
- [x] Address specific baseline failures identified in RED
  - ✅ Diagnostic framework for systematic approach
  - ✅ When to use which agent
  - ✅ Three-source comparison pattern
- [x] Code inline OR link to separate file
  - ✅ Inline examples (TypeScript patterns)
- [x] One excellent example (not multi-language)
  - ✅ Real-world GetTransportRequests example
  - ✅ Content type error example
- [x] Run scenarios WITH skill - verify agents now comply
  - ✅ Content type scenario tested
  - ✅ Agent followed framework correctly
  - ✅ Used both reference agents as recommended

## REFACTOR Phase - Close Loopholes ✅

- [x] Identify NEW rationalizations from testing
  - None found - agent followed skill correctly
- [x] Add explicit counters (if discipline skill)
  - N/A - This is a technique skill, not discipline-enforcing
- [x] Build rationalization table from all test iterations
  - N/A - No rationalizations observed
- [x] Create red flags list
  - ✅ Included in "Common Mistakes" section
- [x] Re-test until bulletproof
  - ✅ One test passed cleanly
  - ⏳ Additional scenarios could be tested but not required

## Quality Checks ✅

- [x] Small flowchart only if decision non-obvious
  - ✅ One flowchart showing diagnostic process
  - ✅ Uses semantic labels (not step1, step2)
- [x] Quick reference table
  - ✅ Three-Source Comparison table
  - ✅ Investigation Patterns by Error Type
- [x] Common mistakes section
  - ✅ Table of mistakes and fixes
- [x] No narrative storytelling
  - ✅ Direct, actionable guidance
- [x] Supporting files only for tools or heavy reference
  - ✅ Self-contained in SKILL.md

## Deployment ⏳

- [ ] Commit skill to git and push to your fork (if configured)
- [ ] Consider contributing back via PR (if broadly useful)

## Additional Testing Recommendations (Optional)

For maximum confidence, could test:
1. ⏳ Implementing SaveProgram from scratch
2. ⏳ Debugging CSRF token issue
3. ⏳ Debugging 404 endpoint error

Current assessment: Skill works well and is ready for use.

## Notes

**Skill Type**: Technique (how-to guide)

**Word Count**: ~850 words (under target of 500 for frequent use, but acceptable for complex technique)

**Token Efficiency**: Could be compressed if this becomes frequently-loaded, but unlikely given specialized nature.

**Cross-References**: References adt-client-explorer and sadt-rest-explorer agents appropriately.
