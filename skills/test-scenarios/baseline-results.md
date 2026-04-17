# Baseline Test Results: MCP Tool Insight

## Scenario 1: GetTransportRequests Returns Empty

### What Agent Did Well
1. ✅ Read the handler implementation first
2. ✅ Checked the transport utils library
3. ✅ Made direct API calls to test behavior
4. ✅ Fixed the actual bug (empty DEVCLASS causing 400 error)
5. ✅ Implemented automatic package fetching

### What Agent DIDN'T Do
1. ❌ Did NOT check Eclipse ADT client implementation
2. ❌ Did NOT check SADT_REST backend implementation
3. ❌ Did NOT use the sadt-rest-explorer agent
4. ❌ Did NOT use the adt-client-explorer agent
5. ❌ Did NOT compare request format to Eclipse client's format
6. ❌ Did NOT investigate backend ABAP content handler expectations

### Time
Approximately 15-20 minutes to identify and fix

### Approach
- Trial-and-error with API calls
- Code reading and logical deduction
- Fixed by understanding error messages

### Key Gap
**Agent solved the problem but didn't leverage reference implementations.**

If this had been a more complex issue (content handlers, CSRF tokens, complex XML formats), the agent would have struggled without:
- Understanding how Eclipse client constructs requests
- Understanding what SADT_REST backend expects
- Patterns from similar tool implementations in both systems

### Why This Matters
For complex debugging:
- Content negotiation issues → Need to see Eclipse client's content handler selection
- Request format issues → Need to see Eclipse client's request building
- Response parsing issues → Need to understand backend's response handler
- New tool implementation → Need both backend endpoint structure and client patterns

## Key Insight for Skill

The agent CAN solve problems through trial-and-error, but would be MORE EFFICIENT with:
1. **Systematic approach** using reference implementations
2. **Agent tools** for exploring backend and client
3. **Comparison framework** (MCP vs Eclipse vs Backend)

## What the Skill Should Teach

### 1. Diagnostic Framework
```
Problem with MCP tool
  ↓
What's failing?
  ↓
Check 3 sources:
1. MCP implementation (current code)
2. Eclipse client (reference implementation)
3. Backend ABAP (what it expects)
  ↓
Compare → Identify mismatch → Fix
```

### 2. When to Use Which Agent
- **sadt-rest-explorer**: Understand what backend expects (content handlers, exceptions, URIs)
- **adt-client-explorer**: Understand how Eclipse does it (request building, session management, CSRF)

### 3. Common Failure Patterns
- Empty/missing parameters → Check Eclipse client's parameter building
- Content type errors → Check Eclipse client's content handler selection
- CSRF token issues → Check Eclipse client's LockRequestUtil pattern
- 404/405 errors → Check backend endpoint structure (object-specific URIs)
- Parse errors → Check backend response format expectations

## Next: Write Minimal Skill

Now write skill that:
1. Provides diagnostic framework
2. Shows when to use which agent
3. Maps error types to investigation approaches
4. Includes comparison patterns
