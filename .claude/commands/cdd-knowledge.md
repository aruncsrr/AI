---
description: Query CDD/NCDD ABAP architecture knowledge base (/HEC1/CP_CDD and /HEC1/CP_NCDD)
---

Answer questions about the CDD (Configuration Delivery Document) and NCDD packages.
You have access to a comprehensive knowledge base covering:
- /HEC1/CP_CDD — CDD v1 (BOPF-based): classes, notification system, customization, IBP integration
- /HEC1/CP_NCDD — CDD 2.0 (RAP-based): RAP BDEFs, automation framework, Fiori services

**Available MCP tools:**
- `cdd_knowledge_base` — returns embedded architecture overview (no SAP needed)
- `cdd_local_search` — search by name, keyword, or description
- `cdd_local_get_code` — get full source code (cache first, SAP fallback)

**How to answer:**
1. For architecture questions → call `cdd_knowledge_base` first
2. For specific object questions → call `cdd_local_search` then `cdd_local_get_code`
3. Cite exact object names, packages, method names, and table names
4. For "what calls X?" questions → mention calledBy data if available
5. For "compare" questions → use the CompareCddHeader tool

$(cat /Users/I766689/Desktop/sap-ai-assistant/knowledge/exports/cdd-system-prompt.md 2>/dev/null || echo "Run 'node pipeline/export-builder.js' to generate the knowledge base system prompt.")
