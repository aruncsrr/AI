# Baseline Test Scenario: MCP Tool Insight

## Purpose
Test how an agent approaches debugging/implementing MCP tools WITHOUT the mcp-tool-insight skill present.

## Scenario 1: Tool Returns Empty Results

**User Message**:
```
The GetTransportRequests tool returns an empty array but I know transport requests exist for my user. Can you investigate why it's not working?
```

**Document**:
- What steps does agent take?
- Do they check backend ABAP implementation?
- Do they compare to Eclipse client implementation?
- Do they examine request/response format?
- Do they find root cause?
- Time to resolution

## Scenario 2: Implementing New Tool

**User Message**:
```
I need to implement a SaveProgram tool that works like SaveClass but for ABAP programs. Help me implement it correctly.
```

**Document**:
- Do they investigate Eclipse client's program save implementation?
- Do they check backend endpoint structure?
- Do they compare to existing SaveClass?
- Do they understand object-specific URI patterns?
- What do they miss?

## Scenario 3: Content Type Mismatch

**User Message**:
```
ActivateObject fails with "Unexpected token '<'" - looks like XML when expecting JSON. Why?
```

**Document**:
- Do they investigate content handlers?
- Do they check Accept/Content-Type headers?
- Do they examine backend content handler implementation?
- Do they look at Eclipse client's content negotiation?

## Recording Template

### Agent Actions (Verbatim)
```
[Record exactly what agent does]
```

### Checks Performed
- [ ] Backend SADT_REST implementation
- [ ] Eclipse ADT client implementation
- [ ] Existing similar tool code
- [ ] Request headers/body
- [ ] Response parsing
- [ ] Error messages from SAP

### Time to Resolution
[X minutes or "couldn't resolve"]

### Gaps/Mistakes
```
[What was missed, wrong assumptions, inefficient paths]
```

### Rationalizations
```
["I'll just try X", "Don't need to check Y", etc.]
```

## Next Steps

After documenting baseline (RED phase):
1. Analyze common patterns in failures
2. Write minimal skill addressing those gaps (GREEN phase)
3. Re-test and close loopholes (REFACTOR phase)
