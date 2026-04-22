#!/usr/bin/env node
/**
 * Export builder — produces:
 *   knowledge/exports/cdd-system-prompt.md    — injected by slash command
 *   knowledge/exports/cdd-rag-chunks.jsonl    — one line per object for vector search
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const config        = JSON.parse(fs.readFileSync(path.join(__dirname, 'pipeline-config.json'), 'utf8'));
const KNOWLEDGE_DIR = path.resolve(__dirname, config.knowledgeDir);
const ABAP_DIR      = path.join(KNOWLEDGE_DIR, 'abap');
const GRAPH_DIR     = path.join(KNOWLEDGE_DIR, 'graph');
const EXPORTS_DIR   = path.join(KNOWLEDGE_DIR, 'exports');

const TYPE_DIR = {
  class: 'classes',
  interface: 'interfaces',
  program: 'programs',
  function_group: 'function-groups',
  cds_view: 'cds-views',
  behavior_definition: 'behavior-definitions',
};

if (!fs.existsSync(EXPORTS_DIR)) fs.mkdirSync(EXPORTS_DIR, { recursive: true });

// Load graph artefacts
function loadJson(p) {
  return fs.existsSync(p) ? JSON.parse(fs.readFileSync(p, 'utf8')) : null;
}

const index     = loadJson(path.join(GRAPH_DIR, 'index.json'))     || {};
const edges     = loadJson(path.join(GRAPH_DIR, 'edges.json'))     || [];
const mindMap   = loadJson(path.join(GRAPH_DIR, 'mind-map.json'))  || {};
const top20     = loadJson(path.join(GRAPH_DIR, 'top-connected.json')) || [];

// Load all records
function loadAllRecords() {
  const records = [];
  for (const dir of Object.values(TYPE_DIR)) {
    const dirPath = path.join(ABAP_DIR, dir);
    if (!fs.existsSync(dirPath)) continue;
    for (const f of fs.readdirSync(dirPath)) {
      if (!f.endsWith('.json')) continue;
      try { records.push(JSON.parse(fs.readFileSync(path.join(dirPath, f), 'utf8'))); }
      catch (e) { /* skip */ }
    }
  }
  return records;
}

const records = loadAllRecords();
const summary = mindMap.summary || {};

// ── Helpers ───────────────────────────────────────────────────────────────────

function groupBy(arr, key) {
  return arr.reduce((acc, item) => {
    const k = item[key] || 'unknown';
    if (!acc[k]) acc[k] = [];
    acc[k].push(item);
    return acc;
  }, {});
}

function patternRecords(pattern) {
  return records.filter(r => r.analysis?.patterns?.includes(pattern));
}

// ── Build system prompt ───────────────────────────────────────────────────────

const lines = [];
const ln = (...parts) => lines.push(parts.join(''));
const sep = () => ln('\n---\n');

ln('# CDD/NCDD ABAP Architecture Knowledge Base');
ln('\nGenerated: ', new Date().toISOString());
ln('\nPackages: /HEC1/CP_CDD (CDD v1, BOPF-based) | /HEC1/CP_NCDD (CDD 2.0, RAP-based)');
ln('\nTotal objects crawled: ', records.length);
sep();

// Package overview
ln('## Package Overview\n');
if (mindMap.packages) {
  const pkgs = Object.values(mindMap.packages).sort((a, b) => b.objectCount - a.objectCount);
  ln('| Package | Objects | Types |');
  ln('|---------|---------|-------|');
  for (const pkg of pkgs) {
    const typeStr = Object.entries(pkg.byType || {}).map(([t, n]) => `${n} ${t}`).join(', ');
    ln(`| \`${pkg.package}\` | ${pkg.objectCount} | ${typeStr} |`);
  }
} else {
  ln('_(package data not available — run graph-builder.js first)_');
}
sep();

// Object counts by type
ln('## Object Counts by Type\n');
ln('| Type | Count |');
ln('|------|-------|');
for (const [type, count] of Object.entries(summary.byType || {})) {
  ln(`| ${type} | ${count} |`);
}
sep();

// Top connected nodes
if (top20.length > 0) {
  ln('## Most-Connected Objects (Top 20)\n');
  ln('| Object | Type | Role | Connections |');
  ln('|--------|------|------|-------------|');
  for (const n of top20) {
    ln(`| \`${n.name}\` | ${n.type || ''} | ${n.role || ''} | ${n.degree} |`);
  }
  sep();
}

// Key patterns with examples
const IMPORTANT_PATTERNS = [
  ['api-facade',           'API Facades — Entry points for external callers'],
  ['factory',              'Factories — Object creation + polymorphic dispatch'],
  ['singleton',            'Singletons — Single-instance managers'],
  ['notification-handler', 'Notification Handlers — Email/event senders'],
  ['validator',            'Validators — Business rule validators'],
  ['status-machine',       'Status Machines — Workflow state machines'],
  ['rap-behavior',         'RAP Behavior Implementations'],
  ['rap-event-handler',    'RAP Event Handlers'],
  ['automation-calculator','Automation Calculators'],
];

ln('## Key Objects by Pattern\n');
for (const [pattern, label] of IMPORTANT_PATTERNS) {
  const recs = patternRecords(pattern);
  if (recs.length === 0) continue;
  ln(`### ${label}\n`);
  for (const r of recs.slice(0, 15)) {
    ln(`- **\`${r.name}\`** (${r.package}) — ${r.analysis?.role || r.description || ''}`);
    if (r.analysis?.keyMethods?.length) {
      ln(`  - Key methods: ${r.analysis.keyMethods.slice(0, 5).join(', ')}`);
    }
    if (r.calledBy?.length) {
      ln(`  - Called by: ${r.calledBy.slice(0, 5).join(', ')}${r.calledBy.length > 5 ? ` (+${r.calledBy.length-5} more)` : ''}`);
    }
  }
  ln('');
}
sep();

// Per-package object lists
ln('## All Objects by Package\n');
const byPkg = groupBy(records, 'package');
for (const [pkg, recs] of Object.entries(byPkg).sort()) {
  ln(`### \`${pkg}\`\n`);
  const byType = groupBy(recs, 'type');
  for (const [type, typeRecs] of Object.entries(byType).sort()) {
    ln(`**${type}s:**`);
    for (const r of typeRecs.sort((a, b) => (a.name || '').localeCompare(b.name || ''))) {
      const role = r.analysis?.role || r.description || '';
      const pats = r.analysis?.patterns?.length ? ` [${r.analysis.patterns.join(', ')}]` : '';
      ln(`- \`${r.name}\` — ${role}${pats}`);
    }
    ln('');
  }
}
sep();

// Dependency summary (interfaces and their callers)
const interfaces = records.filter(r => r.type === 'interface' && r.calledBy?.length > 0);
if (interfaces.length > 0) {
  ln('## Interface Dependency Map\n');
  ln('| Interface | Called By |');
  ln('|-----------|-----------|');
  for (const intf of interfaces.sort((a, b) => (b.calledBy?.length||0) - (a.calledBy?.length||0))) {
    ln(`| \`${intf.name}\` | ${(intf.calledBy||[]).slice(0, 5).join(', ')} |`);
  }
  sep();
}

// ── Enriched domain knowledge sections ───────────────────────────────────────

// 1. Email / Notification triggers
ln('## Email & Notification System\n');
ln(`
### CDD v1 — Email Architecture
- **Orchestrator:** \`/HEC1/CL_CDD_EMAIL\` (BCS-based, uses \`/HEC1/CL_CDD_EMAIL_TEMPLATE.SEND_MAIL\`)
- **Trigger method:** \`/HEC1/IF_CDD_EMAIL~EVENT_CDD(iv_event_id, is_cdd_head)\`
- **Caller in manager:** \`/HEC1/CL_CDD_MANAGER.event_cdd()\` and \`event_cdd20()\`
- **CTC notification:** \`/HEC1/CL_CDD_CTC_NOTIF\` — batch selection + BCS send with UPDATE_ACTION_LOG

### CDD v1 — Business Email Event IDs (gc_business_event_id in /HEC1/IF_CDD_CONSTANTS)
| Event ID | When Triggered |
|----------|---------------|
| \`REQUESTED\` | New CDD request created by CAA — added to Work-Basket |
| \`ASSIGNED\` | CDD request assigned to a person for processing |
| \`SEND_TO_APPROVE\` | GDO Lead Architect completes processing, sends for approval |
| \`APPROVED\` | CDD/CR response approved |
| \`REJECTED\` | CDD/CR response rejected |
| \`REJECTED_INT\` | Sent back to GDO Lead Architect for corrections |
| \`REJECTED_INT_GDO\` | Sent back for corrections (GDO path) |
| \`DR_REQUESTED\` | DR systems request raised |
| \`DR_READY_FOR_PROC\` | Primary CDD complete, DR ready for processing |
| \`DR_SEND_TO_APPROVE\` | DR Architect sends DR CDD for approval |
| \`DR_REJECTED_INT\` | DR request sent back to DR Architect for corrections |
| \`IBP_CONFIRMED\` | IBP confirms capacity on tier level |
| \`IBP_CAPA_CONFIRM\` | IBP capacity confirmation update |
| \`IBP_REQUESTED\` | IBP request triggered |
| \`INVALIDATED\` | CDD request invalidated |
| \`MAINTAIN_SUBCON\` | Subcontractor maintenance triggered (ITSHECS4HANA-26672) |
| \`CR_OUTCB_REQUESTED\` | CR team informed when CR requested with outcome-based processing |
| \`CR_OUTCB_SEND2APPR\` | Region-specific GDO CDD Approvers notified |

### CDD 2.0 (NCDD) — Notification
- **RAP event:** \`/HEC1/CL_NCDD_REQUEST_CREATE~event_cddprocesscomplete(gs_ncdd_header)\`
- Raised when NCDD request processing completes
`);
sep();

// 2. Feature toggles / parameters
ln('## Feature Toggles & Runtime Parameters\n');
ln(`
### Toggle Mechanism
- **Storage table:** \`/HEC1/CDD_PARAM\` (fields: PARAM_NAME, SYSID, UNAME, XACTIVE, PARAM_VALUE, PARAM_TEXT)
- **Read via:** programs \`/HEC1/CDD_PARAM_ACTIVATE\`, \`/HEC1/CDD_PARAM_DEACTIVATE\`, \`/HEC1/CDD_PARAM_SET_VALUE\`, \`/HEC1/CDD_PARAM_DELETE\`
- In code: parameter values read from \`/HEC1/CDD_PARAM\` using PARAM_NAME keys

### Key Feature Toggle Parameters (gc_param_name in /HEC1/IF_CDD_CONSTANTS)
| Parameter Name | Purpose |
|---------------|---------|
| \`CDD_AUTHORIZATION_CHECK\` | Enable/disable auth check |
| \`CDD_AUTHORIZATION_CHECK_BY_ROLE%\` | Role-based auth check |
| \`CDD_CR_AUTO_APPROVAL\` | Auto-approval for Change Requests |
| \`CDD_BOPF_ASYNC\` | Async BOPF processing |
| \`CDD_CONFIG_BUFFER\` | Config buffer enable |
| \`CDD_CR_MULTI_TEAM\` | Multi-team CR processing |
| \`CDD_CR_MULTI_TEAM_DR\` | Multi-team DR processing |
| \`CDD_PARTIAL_APPROVAL\` | Partial approval flow |
| \`CDD_HW_BUILD_ACTIVE\` | HW Build feature active |
| \`CDD_PPLAN_DNS_CHECK\` | DNS check in project plan |
| \`CDD_ADD_SERVICES\` | Additional services visible |
| \`CDD_ADD_SERVICES_HIDDEN\` | Hide additional services |
| \`CDD_LTB\` | Long-term backup feature |
| \`CDD_LTB_HIDDEN\` | Hide LTB |
| \`CDD_CONN\` | Connectivity component |
| \`CDD_CONN_HIDDEN\` | Hide connectivity |
| \`CDD_ASSUM_TEMPLATE_V2\` | Assumption template v2 UI |
| \`CDD_RENEWAL01/02/03\` | Renewal process variants |
| \`CDD_NOTIF_SEND_EVNT\` | Notification send event flag |
| \`EMAIL_NEW_CDD\` | Email on new CDD creation |
| \`CDD_PHASE_OPTIMIZE\` / \`CDD_PHASE_OPTIMIZE_01..07\` | Phase calculation optimizations |
| \`CDD_BOPF_UPDATE_CORR01\` | BOPF update correction |
| \`CDD_CR_RD2112/2202/2206\` | Release-specific CR features |
`);
sep();

// 3. Customization tables
ln('## Customization & Configuration Tables\n');
ln(`
### CDD v1 Core Data Tables
| Table | Description |
|-------|-------------|
| \`/HEC1/CDD_HEAD\` | CDD request header (main entity) |
| \`/HEC1/CDD_CR\` | Change Request data |
| \`/HEC1/CDD_CRC\` | CDD CRC mapping |
| \`/HEC1/CDD_CRT\` | CR text table |
| \`/HEC1/CDD_PPLAN\` | Project plan entries |
| \`/HEC1/CDD_REMARK\` | Remarks per request |
| \`/HEC1/CDD_RISK\` | Risk entries |
| \`/HEC1/CDD_ASSUM\` | Assumptions |
| \`/HEC1/CDD_ASSUMT\` | Assumption template |
| \`/HEC1/CDD_ASSUTP\` | Assumption template types |
| \`/HEC1/CDD_RESPL\` | Responsible persons |
| \`/HEC1/CDD_TEAM\` | Team assignments |
| \`/HEC1/CDD_TEAMS\` | Team definitions |
| \`/HEC1/CDD_TCOMP\` | Tier component data |
| \`/HEC1/CDD_ACTION\` | Audit log (all state changes + notifications) |
| \`/HEC1/CDD_ARC\` | Archive data |
| \`/HEC1/CDD_RESULT\` | Configuration result |
| \`/HEC1/CDD_DOWN\` | Downtime data |
| \`/HEC1/CDD_HWPLAN\` | HW build plan |
| \`/HEC1/CDD_MLOG\` | Modification log |
| \`/HEC1/CDD_FINAL\` | Final data |
| \`/HEC1/CDD_REQTYP\` | Request types |

### CDD v1 Customizing Tables
| Table | Description |
|-------|-------------|
| \`/HEC1/CDD_CUSTOM\` | Generic customizing (key1/key2/key3 + email subjects, recipients) |
| \`/HEC1/CDD_PARAM\` | Feature toggle parameters (PARAM_NAME, SYSID, XACTIVE, PARAM_VALUE) |
| \`/HEC1/CDDBOPFCNF\` | BOPF node/field mapping configuration |
| \`/HEC1/CDD_C_GDO_RL\` | GDO role/region customizing (RAP-managed, with draft) |
| \`/HEC1/CDD_IBP_F\` | IBP filter configuration |
| \`/HEC1/CDD_IBP_RQ\` | IBP request |
| \`/HEC1/CDD_IBP_RS\` | IBP response |

### CDD 2.0 (NCDD) Core Data Tables
| Table | Description |
|-------|-------------|
| \`/HEC1/NCDD_HEAD\` | NCDD header |
| \`/HEC1/NCDD_PPLAN\` | Project plan |
| \`/HEC1/NCDD_ASSUM\` | Assumptions |
| \`/HEC1/NCDD_RISK\` | Risks |
| \`/HEC1/NCDD_RESPL\` | Responsible persons |
| \`/HEC1/NCDD_REMRK\` | Remarks |
| \`/HEC1/NCDD_CONT\` | Contacts |
| \`/HEC1/NCDD_CTLOG\` | Change log |
| \`/HEC1/NCDD_STLOG\` | Status log |
| \`/HEC1/NCDD_ACTN\` | Actions |
| \`/HEC1/NCDD_CRDES\` | CR description |
| \`/HEC1/NCDD_FIELD\` | Field customization |
| \`/HEC1/NCDD_MAPF\` | Field mapping |
| \`/HEC1/NCDD_VARV\` | Variables/mapping table (ABAP variant variables) |
| \`/HEC1/NCDD_PROC\` | Process customizing |
| \`/HEC1/NCDD_TMDT\` | Timeline/date data |
| \`/HEC1/NCDD_WORKD\` | Worklist data |
| \`/HEC1/NCDD_COMPC\` | Component configuration |
| \`/HEC1/NCDD_CHARC\` | Characteristics |
| \`/HEC1/NCDD_NODES\` | Node structure |
`);
sep();

// 4. UI components
ln('## UI Components\n');
ln(`
### CDD v1 — Web Dynpro FPM UI (package /HEC1/CP_CDD_UI)
CDD v1 uses FPM (Floorplan Manager) with GUIBB blocks. Main entry: \`/HEC1/START_CDD_COCKPIT\` program.

**Form UIBBs (GUIBBF — detail/edit forms):**
| Class | Purpose |
|-------|---------|
| \`/HEC1/CL_CDD_GUIBBF_GENERAL_IN\` | General information form |
| \`/HEC1/CL_CDD_GUIBBF_APPROVAL\` | Approval form |
| \`/HEC1/CL_CDD_GUIBBF_PRE_APPROV\` | Pre-approval form |
| \`/HEC1/CL_CDD_GUIBBF_REJECTION\` | Rejection form |
| \`/HEC1/CL_CDD_GUIBBF_REMARK\` | Remarks form |
| \`/HEC1/CL_CDD_GUIBBF_DOWNTIME\` | Downtime form |
| \`/HEC1/CL_CDD_GUIBBF_STATUSTIER\` | Status/tier form (status machine) |
| \`/HEC1/CL_CDD_GUIBBF_CONF_VALI\` | Configuration validation form |
| \`/HEC1/CL_CDD_GUIBBF_PPLAN_DETN\` | Project plan detail |

**List UIBBs (GUIBBL — tables/lists):**
| Class | Purpose |
|-------|---------|
| \`/HEC1/CL_CDD_GUIBBL_ENTRY\` | Work basket entry list (main overview) |
| \`/HEC1/CL_CDD_GUIBBL_OVERVIEW\` | Overview list |
| \`/HEC1/CL_CDD_GUIBBL_PPLAN\` | Project plan list |
| \`/HEC1/CL_CDD_GUIBBL_PPLAN_DET\` | Project plan detail list |
| \`/HEC1/CL_CDD_GUIBBL_ASSUMPTION\` | Assumptions list |
| \`/HEC1/CL_CDD_GUIBBL_ASSUMPTV2\` | Assumptions v2 list |
| \`/HEC1/CL_CDD_GUIBBL_ASSUM_TMPL\` | Assumption template list |
| \`/HEC1/CL_CDD_GUIBBL_RISK\` | Risk list |
| \`/HEC1/CL_CDD_GUIBBL_RESPL\` | Responsible persons list |
| \`/HEC1/CL_CDD_GUIBBL_CR_PPLAN\` | CR project plan list |
| \`/HEC1/CL_CDD_GUIBBL_CONF_SEL\` | Configuration selection |
| \`/HEC1/CL_CDD_GUIBBL_TIER_COMP\` | Tier comparison |
| \`/HEC1/CL_CDD_GUIBBL_VERS_COMP\` | Version comparison |
| \`/HEC1/CL_CDD_GUIBBL_TEMPLATE\` | Template list (most connected: 26 edges) |

**Composite UIBBs (GUIBBC):**
- \`/HEC1/CL_CDD_GUIBBC_PPLAN\` — Composite project plan view (show/hide detail)

**FPM App Controller:** \`/HEC1/CL_CDD_FPM_APPCC\` — manages edit mode, events, OVP title
**FPM Model:** \`/HEC1/CL_CDD_FPM_MODEL\` — business logic for UI actions (save, archive, copy, auto-approval)

**FPM Events (gc_events in /HEC1/IF_CDD_CONSTANTS — HEC_CDD_* prefix):**
approve, approve_final, approve_partial, reject_int, reject_ext, rejection, pre_approve, edit,
pplan_save, pplan_compare, pplan_copy_entry, pplan_del_entry, version_compare, download_config_xls,
download_proposal_pdf, show_configuration, show_landscape, cdd_subcon_sel, cdd_lco_edit, refresh

### CDD 2.0 (NCDD) — Fiori Elements / RAP UI
Entry point: \`/HEC1/C_NCDD_WORKLIST\` (projection BDEF) + \`/HEC1/I_NCDD_WORKLIST\` (base BDEF, managed + draft)
Behavior implementation: \`/HEC1/BP_I_NCDD_WORKLIST_G\`

**Key Fiori/OData classes:**
| Class | Purpose |
|-------|---------|
| \`/HEC1/CL_NCDD_OUTPUT_MPC\` | OData metadata provider |
| \`/HEC1/CL_NCDD_OUTPUT_DPC\` | OData data provider |
| \`/HEC1/CL_NCDD_OUTPUT_DPC_EXT\` | OData DPC extension |
| \`/HEC1/CL_NCDD_EXCEL_MPC\` | Excel export MPC |
| \`/HEC1/CL_NCDD_EXCEL_DPC\` | Excel export DPC |
| \`/HEC1/CL_NCDD_EXCEL_MPC_EXT\` | Excel export MPC extension |
| \`/HEC1/CL_NCDD_EXCEL_DPC_EXT\` | Excel export DPC extension |
`);
sep();

// 5. RAP objects
ln('## RAP Objects (CDD 2.0 / NCDD)\n');
ln(`
### Behavior Definitions
| BDEF | Type | Implementation Class | Notes |
|------|------|---------------------|-------|
| \`/HEC1/I_NCDD_WORKLIST\` | Base (managed, draft) | \`/hec1/bp_i_ncdd_worklist_g\` | Core NCDD worklist |
| \`/HEC1/C_NCDD_WORKLIST\` | Projection | — | With draft, side effects |
| \`/HEC1/I_NCDD_WORKLIST_N\` | Base (managed) | \`/hec1/bp_i_ncdd_worklist_n\` | New variant (TEMP package) |
| \`/HEC1/C_NCDD_WORKLIST_N\` | Projection | — | New variant |
| \`/HEC1/CDD_R_GDO_RL\` | Base (managed, draft) | \`/HEC1/BP_CDD_R_GDO_RL\` | GDO Role config with draft |
| \`/HEC1/CDD_C_GDO_RL\` | Projection | — | Draft-enabled, strict(2) |

### Key RAP Implementation Classes
- **\`/HEC1/BP_I_NCDD_WORKLIST_G\`** — main NCDD behavior implementation
- **\`/HEC1/BPCL_I_NCDD_DATA_CTR\`** — NCDD data center behavior
- **\`/HEC1/BPCL_I_NCDD_GEN_INFO\`** — NCDD general info behavior
- **\`/HEC1/BPCL_I_PRICE_ADJST_TIER\`** — Pricing adjustment tier

### CDS View Naming Convention (NCDD)
| Prefix | Role |
|--------|------|
| \`I_NCDD_*\` | Interface/base views (transactional data source) |
| \`C_NCDD_*\` | Consumption views (Fiori/OData exposed) |
| \`R_NCDD_*\` | Reuse views (shared data selections) |
| \`I_CDD_*\` | CDD v1 interface views |
| \`C_CDD_*\` | CDD v1 consumption views |
`);
sep();

// 6. Reports / batch programs
ln('## Reports & Batch Programs\n');
ln(`
### CDD v1 Reports & Admin Programs (/HEC1/CP_CDD_APP + /HEC1/CP_CDD_REPORTING)
| Program | Purpose |
|---------|---------|
| \`/HEC1/CDD_CREATE_REQUEST\` | Create CDD request |
| \`/HEC1/CDD_SET_STATUS\` | Mass status change |
| \`/HEC1/CDD_SET_STATUS_COMMENT\` | Status change with comment |
| \`/HEC1/CDD_SET_PPLAN_VALUES\` | Update project plan values |
| \`/HEC1/CDD_SET_TIER_NSB_FLAG\` | Set NSB flag on tier |
| \`/HEC1/CDD_SET_S2DHO_DATE\` | Set S2D handover date |
| \`/HEC1/CDD_SET_VALIDITY_DATE\` | Set validity date |
| \`/HEC1/CDD_SET_CURRENT_TEAM\` | Set current team |
| \`/HEC1/CDD_SET_TEAM_ASSIGNMENT\` | Mass team assignment |
| \`/HEC1/CDD_BOPF_UPDATE\` | BOPF mass update (ALV report) |
| \`/HEC1/CDD_BOPF_UPDATE_BY_JOB\` | BOPF update as background job |
| \`/HEC1/CDD_BOPF_DISPLAY\` | BOPF data display |
| \`/HEC1/CDD_COMPARE_TIER\` | Tier comparison report |
| \`/HEC1/CDD_COMPARE_TIER2\` | Tier comparison v2 |
| \`/HEC1/CDD_CREATE_RESULT_LIST\` | Create result list |
| \`/HEC1/CDD_CREATE_RESULT_ENTRY\` | Create single result entry |
| \`/HEC1/CDD_DELETE_RESULT_ENTRY\` | Delete result entry |
| \`/HEC1/CDD_CREATE_ARCHIVE\` | Archive CDD data |
| \`/HEC1/CDD_INVALIDATE_REQUEST\` | Invalidate CDD request |
| \`/HEC1/CDD_PARAM_ACTIVATE\` | Activate feature parameter |
| \`/HEC1/CDD_PARAM_DEACTIVATE\` | Deactivate feature parameter |
| \`/HEC1/CDD_PARAM_SET_VALUE\` | Set parameter value |
| \`/HEC1/CDD_PARAM_DELETE\` | Delete parameter |
| \`/HEC1/CDD_UPDATE_ADDNL_CONFIG\` | Update additional config |
| \`/HEC1/CDD_CTC_ASSIGN_NOTIF\` | CTC — assign + send notification (reporting) |
| \`/HEC1/CDD_MIG_CDD_ASSUM_01\` | Migration: assumptions |
| \`/HEC1/CDD_MIG_CDD_REQUEST_TYPE\` | Migration: request type |
| \`/HEC1/CDD_MIG_CDD_XTEAM\` | Migration: cross-team |

### IBP Integration Programs (/HEC1/CP_CDD_IBP)
| Program | Purpose |
|---------|---------|
| \`/HEC1/CDD_IBP_CREATE_REQUEST\` | Create IBP request |
| \`/HEC1/CDD_IBP_PROCESS_INBOUND\` | Process IBP inbound data |
| \`/HEC1/CDD_IBP_CREATE_FILTER\` | Create IBP filter |
| \`/HEC1/IBP_ATP_CALL_PROPOSAL\` | IBP ATP capacity call |
| \`/HEC1/IBP_PROC_INBOUND_INTERF\` | IBP inbound interface processor |

### NCDD Reports (/HEC1/CP_NCDD_APP, /HEC1/CP_NCDD_AUTO)
| Program | Purpose |
|---------|---------|
| \`/HEC1/NCDD_BOPF_UPDATE\` | NCDD BOPF mass update |
| \`/HEC1/NCDD_HEADER_UPDATE\` | NCDD header mass update |
| \`/HEC1/NCDD_SET_PPLAN_VALUES\` | Set project plan values |
| \`/HEC1/NCDD_DELETE_CDD_DATA\` | Delete CDD data |
| \`/HEC1/NCDD_AUTOMATION_TEST\` | Automation test runner |
| \`/HEC1/NCDD_AUTOMATION_DEL_RES\` | Delete automation results |

### UI Entry Programs
| Program | Purpose |
|---------|---------|
| \`/HEC1/START_CDD_COCKPIT\` | Launch CDD Cockpit (main Web Dynpro UI) |
| \`/HEC1/START_CDD_ASSUM_TEMPL\` | Launch assumption template editor |
`);
sep();

// 7. Domains
ln('## Domain Values (CDD/NCDD — from SAP DDIC)\n');
ln(`
### CDD v1 Status (/HEC1/CDD_STATUS — NUMC 2)
| Code | Meaning |
|------|---------|
| 00 | CDD requested |
| 02 | CDD in process |
| 05 | CDD in process (alt.) |
| 15 | CDD DR requested |
| 20 | CDD DR Approval requested |
| 30 | CDD Final Approval requested |
| 80 | CDD rejected (Internal) |
| 85 | CDD rejected (External) |
| 86 | CDD Cancelled |
| 88 | CDD partly rejected |
| 90 | CDD Process completed |
| 93 | Hardware Availability Date requested |
| 94 | Hardware Availability Date temporary set |
| 98 | CDD invalidated |

### NCDD Status (/HEC1/NCDD_STATUS — NUMC 2)
| Code | Meaning |
|------|---------|
| 00 | CDD Requested |
| 02 | CDD in Process |
| 30 | CDD Final Approval Requested |
| 80 | CDD Rejected (Internal) |
| 85 | CDD Rejected (External) |
| 86 | CDD Cancelled |
| 90 | CDD Process Completed |

### Request Types (/HEC1/CDD_REQUEST_TYPE — CHAR 10)
| Code | Text |
|------|------|
| INIT_DEAL | Initial Deal |
| CR_NEW_TIE | CR-New/Change Planned Tier |
| CR_LIVE_T | CR-Change Live Tier |
| CR_NON_TIE | CR-Add/Change non-Tier |
| CR_OUTCOME | CR-Outcome based |
| CR_RENEWAL | Auto Renewal |
| CR_ACRENEW | Active Renewal |
| CR_NEW_T | CR-New Tier |
| CR_CHG_T | CR-Change Existing Tier |
| CR_ADD_CHG | CR-Outcome based Request |
| CR_CONNECT | CR-Change/Add Connectivity |
| CR_ADDSERV | CR-Change Additional Services |
| CR_LTB | CR-LTB |
| SOW | New Contract **OLD** (deprecated) |
| TECH_RESTR | Technical Restructure |
| TECH_CR | Technical CR |
| ADD_PRC_AD | Add Pricing Adjustments |
| COM_CHANGE | Commercial Change |

### Team IDs (/HEC1/CDD_TEAM_ID — NUMC 1)
| Code | Team |
|------|------|
| 0 | GDO |
| 1 | DR |
| 2 | CR |
| 8 | CR Lead (Final Approver) |
| 9 | GDO Lead (Final Approver) |

### Team Status (/HEC1/CDD_STATUS_TIER — CHAR 1)
- blank = No status set, A = Approved, R = Rejected

### Responsible Role (/HEC1/CDD_RESPL_ROLE — CHAR 10)
CAA, CAAM, CAA_TL, DR_A, GDO_LA, GDO_CDD_RL, NSB, MISC, PROC_CDD, PROC_CR, PROC_DR, PROC_FIN

### NCDD Item Status (/HEC1/NCDD_ITEM_STATUS — CHAR 1)
blank=No status, 1=Auto Approved, 2=Best Possible CDD, 3=Best Possible CDD Manually, 4=Manually Approved, 5=Rejected, 6=Timeline not approved

### Risk Type (/HEC1/CDD_RISK_TYPE — CHAR 5)
blank=Primary CDD, DR=DR CDD

### Assumption Condition (/HEC1/CDD_ASSUMPTION_COND — CHAR 2)
0=All, 1=Greenfield, 2=Brownfield - copy from other tier

### NCDD Contact Roles (/HEC1/NCDD_CONTACT_ROLE — CHAR 4)
52 roles including: CAA, ARCH, CAAM, CDM, CDMB, CDMM, CNIT, CTC, DBEN, DCAA, DCAM, DNS, EMS, GXPO, GXPB, GXQM, GXQB, IAE, MAIN, NET, OTH, PL, PLM, PROJ, RDE, SPM, SCFA, SCFB, SCFR, TDD, TECH, TSM, TSMB, TSMM, 247, GMMN, GMEX, GUMN, GUEX

### Other Domains
| Domain | Purpose |
|--------|---------|
| \`/HEC1/NCDD_CHANGE_TYPE\` | Type of change being requested |
| \`/HEC1/NCDD_IMPLEMENTATION_TYPE\` | Implementation type |
| \`/HEC1/NCDD_GXP\` | GxP flag |
| \`/HEC1/NCDD_PPLAN_REJ_RSN\` | Project plan rejection reasons (no fixed values) |
| \`/HEC1/CDD_AUTH_APPL\` | Authorization application |
| \`/HEC1/CDD_AUTH_FUNCTION\` | Authorization function |
| \`/HEC1/CDD_AUTH_EDIT_STATUS\` | Editable status values |
`);
sep();

// 7b. Scenario → Request Type mapping
ln('## CDD Request Type Scenario Mapping (/HEC1/CDD_REQTYP)\n');
ln(`
The table /HEC1/CDD_REQTYP maps scenario numbers (001-028) to request type codes based on flag combinations.

| Scenario | Request Type | IS_CR | MULTI_TEAM | TIER_LIVE | TIER_NOT_LIVE | NON_TIER | TECH_CR | CONTRACT_STATUS |
|----------|-------------|-------|------------|-----------|---------------|----------|---------|-----------------|
| 001 | INIT_DEAL | | | | | | | |
| 002 | CR_NEW_TIE | X | | | X | | | |
| 003 | CR_NEW_TIE | X | | | X | X | | |
| 004 | CR_LIVE_T | X | | X | | X | | |
| 005 | CR_LIVE_T | X | | X | | | | |
| 006 | CR_NON_TIE | X | | | | X | | |
| 007 | CR_OUTCOME | X | X | | | | | |
| 009 | CR_RENEWAL | X | | | | | | 04 |
| 010 | INIT_DEAL | | X | | | | | |
| 011 | CR_ACRENEW | X | X | | | | | 06 |
| 012-014 | CR_ACRENEW | X | | | | | | 06 |
| 015 | CR_ACRENEW | X | X | | | X | | 06 |
| 016 | TECH_RESTR | | | | | | | 09 |
| 017 | TECH_RESTR | | X | | | | | 09 |
| 018-021, 026-027 | TECH_CR | X | varies | varies | | varies | X | |
| 022-023 | ADD_PRC_AD | X | | | | | | PRICE_ADJ |
| 024-025 | COM_CHANGE | X | varies | | | | | 07 |

**Groupings:** Initial Deal (001,010) | Standard CR (002-007) | Renewals (009,011-015) | Technical (016-021,026-027) | Pricing (022-023) | Commercial (024-025)
`);
sep();

// 7c. NCDD Team → Role mapping
ln('## NCDD Processor Team Mapping (/HEC1/NCDD_PROC)\n');
ln(`
| Team ID | Team | Role | Role Description |
|---------|------|------|-----------------|
| 0 | GDO | PROC_CDD | CDD Processor |
| 1 | DR | PROC_DR | CDD DR Processor |
| 2 | CR | PROC_CR | CDD CR Processor |
| 8 | CR Lead | PROC_FIN | CDD Approver (Final) |
| 9 | GDO Lead | PROC_FIN | CDD Approver (Final) |

Teams 0/1/2 are working-level processors. Teams 8/9 are final approvers (both map to PROC_FIN).
`);
sep();

// 8. Authorization / roles
ln('## Authorization Concept\n');
ln(`
### Auth Check Class
- **\`/HEC1/CL_CDD_AUTH_CHECK\`** (/HEC1/CP_CDD_APP) — central authorization check
  - Uses \`/HEC1/CDD_AUTH_FUNCTION\` and \`/HEC1/CDD_AUTH_EDIT_STATUS\` domains
  - Parameter: \`CDD_AUTHORIZATION_CHECK\` in /HEC1/CDD_PARAM (can be disabled)
  - Logging controlled by: \`CDD_AUTHORIZATION_LOG\` parameter

### Auth Object
- SAP auth object: \`0E1960E5C37063CE5D9D4474C5B8FEHT\` (SUSH entry in /HEC1/CP_CDD_DDIC)

### Authorization Parameters
- \`CDD_AUTHORIZATION_CHECK\` — master switch for authorization checks
- \`CDD_AUTHORIZATION_ACCEPT\` — accept mode
- \`CDD_AUTHORIZATION_CHECK_BY_ROLE%\` — role-based check (ITSHECS4HANA-32584)
- \`CDD_AUTHORIZATION_LOG\` — log all auth checks
`);
sep();

// 9. CDD v1 vs NCDD comparison
ln('## CDD v1 vs CDD 2.0 (NCDD) Comparison\n');
ln(`
| Aspect | CDD v1 (/HEC1/CP_CDD_*) | CDD 2.0 NCDD (/HEC1/CP_NCDD_*) |
|--------|------------------------|--------------------------------|
| Framework | BOPF | RAP (managed, with draft) |
| UI Technology | Web Dynpro FPM GUIBB | Fiori Elements (OData V4) |
| Main UI entry | \`/HEC1/START_CDD_COCKPIT\` | \`/HEC1/C_NCDD_WORKLIST\` BDEF |
| Manager class | \`/HEC1/CL_CDD_MANAGER\` | \`/HEC1/CL_NCDD_MANAGER\` |
| BDEF | — | \`/HEC1/I_NCDD_WORKLIST\` (managed+draft) |
| Automation | Manual + \`CL_CDD_AUTO_APPROVAL\` | Factory pattern: \`CL_NCDD_AUTO_FACTORY\` |
| Draft support | No | Yes (with \`/HEC1/NCDD_*D\` draft tables) |
| Email events | IF_CDD_EMAIL~EVENT_CDD | CL_NCDD_REQUEST_CREATE~event_cddprocesscomplete |
| Config table | \`/HEC1/CDD_CUSTOM\` | \`/HEC1/NCDD_VARV\` + \`/HEC1/NCDD_FIELD\` |
| IBP integration | Yes (\`/HEC1/CP_CDD_IBP\`) | No (separate) |
| GDO roles | \`/HEC1/CDD_C_GDO_RL\` (RAP projection) | — |
`);
sep();

// 9b. Key DDIC table fields
ln('## Key Table Field Structures\n');
ln(`
### /HEC1/CDD_REQTYP — Request Type Determination
MANDT, SCENARIO(key,NUMC3), IS_CR, MULTI_TEAM, TIER_LIVE, TIER_NOT_LIVE, NON_TIER_CHANGE, TECH_CR, OVERALL_CHANGE(CHAR10), CONTRACT_STATUS(CHAR2), REQUEST_TYPE(CHAR10)

### /HEC1/NCDD_HEAD — NCDD Header
Key: MANDT + DB_KEY(RAW16 UUID). Fields: REQUEST_TYPE, STATUS(NUMC2), RISK_TYPE, SCENARIO, CREATED_BY/AT, CHANGED_BY/AT, CONTRACT_ID, OPPORTUNITY_ID, SLA_DATE, COMMENT, APPROVAL_DATE, REJECTION_REASON

### /HEC1/NCDD_PPLAN — NCDD Project Plan
Key: MANDT + DB_KEY(RAW16). PARENT_KEY(RAW16→NCDD_HEAD), ROW_TYPE, TEAM(NUMC1), STATUS(CHAR1), PLANNED_START/END, ACTUAL_START/END, DURATION, PROCESSOR, REJECTION_REASON, AUTO_APPROVED

### /HEC1/CDD_TEAMS — Team Configuration
Key: MANDT + RULE_NUMBER(NUMC3). Fields: PPLAN_ROW_TYPE, CR_PROCESSING, DR_TIER, NETWORK, NETWORK_DR, CHECK_TIER_LIVE, CHANGE_INIT_BUILD, PHASED_COMP, TEAM_ID(NUMC1), SORT_ORDER

### /HEC1/CDD_ASSUTP — Assumption Templates
Key: MANDT + ASSUMPTION_NO(NUMC3). Fields: ASSUMPTION_REQTYPE, ASSUMPTION_DC, ASSUMPTION_IAAS, ASSUMPTION_OFFER, ASSUMPTION_PRIO, ASSUMPTION_WR, ASSUMPTION_SOL, ASSUMPTION_COND(CHAR2), ASSUMPTION_TEXT

### /HEC1/NCDD_PROC — Processor Teams (5 rows)
Key: MANDT + TEAM(NUMC1). Fields: ROLE(CHAR10)

### /HEC1/IBP_SRVTYP — IBP Server Types
Key: MANDT + HEC_ROW_COUNT(INT4). Fields: CATEGORY, CORES_DESCR, PRDSERIES, RAM, RACK_UNIT, SERVER_MODEL, SERVER_MODEL_FREE_TEXT, CORES, SOCKETS
`);
sep();

// 9c. Message classes
ln('## HEC1 Message Classes\n');
ln(`
### Core CDD Messages — /HEC1/CDD (29 messages)
| Nr | Text |
|----|------|
| 001 | New CDD-Request for Configuration &1, Version &2, not saved. |
| 002 | New CDD-Request created. |
| 003 | Open CDD-Request for Configuration &1 exists. |
| 010 | INTERNAL ERROR: CDD Request &1/&2/&3 does not exist. |
| 011 | ERROR: CDD-Request already in Status "&1" - Rejection not possible. |
| 013 | Internal Rejection of CDD-Request processed. |
| 015 | Project Plan saved. |
| 016 | Project Plan is not finished yet. Please enter all relevant dates. |
| 017 | CDD Approval requested. |
| 019 | External Rejection of CDD-Request processed. |
| 026 | Set Status "in process" for CDD Request. |

### NCDD Messages — /HEC1/NCDD_MSG
| Nr | Text |
|----|------|
| 011 | Item Status Set as &1(&2). &3 &4 |

### NCDD Automation — /HEC1/NCDD_AUTO
| Nr | Text |
|----|------|
| 037 | Duration &1, Duration Add. &2, Days before start &3 |
| 048 | No Auto approval: signature date too close to earliest build start (IPD) |
| 049 | Build Start Date is derived from SLA Phase Start Date |
| 050 | CDD Date is derived from SLA Phase End Date |
| 070 | Item Status set to &1 for &2 |

### Notification Messages — /HEC1/NOTIF
| Nr | Text |
|----|------|
| 012 | No Recipients found for &1 |
| 013 | No Template found for &1 |
| 026 | Notifications preconditions have not been met |

### Other Key Classes
| Class | Count | Domain |
|-------|-------|--------|
| /HEC1/BO_CONFIG | 152 | BOPF configuration actions/errors |
| /HEC1/APM_HANDLING | 164 | APM node management |
| /HEC1/API_CONF | 43 | API-based configuration |
| /HEC1/APM_BACKUP | 19 | Backup/restore operations |
| /HEC1/AATP | 18 | Advanced ATP availability check |
| /HEC1/MSG_PROV | ~10 | Provisioning messages |
| /HEC1/UTILS | 2 | User not authorized / Customizing not maintained |
`);
sep();

// 10. Authorization
ln('## Authorization Objects & Roles\n');
ln(`
### Authorization Objects (14 — package /HEC1/AUTHORIZATION_OBJECT)

| Object | Description |
|--------|-------------|
| \`/HEC1/CDAP\` | CDD Application (used in auth_check_application) |
| \`/HEC1/CDDE\` | CDD Edit function (checks team_id) |
| \`/HEC1/CDFE\` | CDD External Function |
| \`/HEC1/CDFU\` | CDD UI Function |
| \`/HEC1/CDTE\` | CDD Team-ID — **core field used in all checks** |
| \`/HEC1/CDAA\` | CDD Application Assignment |
| \`/HEC1/CDRT\` | CDD Request Type |
| \`/HEC1/CDVI\` | CDD View |
| \`/HEC1/CONF\` | Configurator Authorization |
| \`/HEC1/CFGA\` | Configuration Authorization |
| \`/HEC1/BCUA\` | Business Configuration User Assignment |
| \`/HEC1/BPUA\` | Business Partner User Assignment |
| \`/HEC1/INST\` | Installation |
| \`/HEC1/SUIB\` | System User Info Base |

### Auth Check Class: /HEC1/CL_CDD_AUTH_CHECK (/HEC1/CP_CDD_APP)

| Method | Auth Object | Checks |
|--------|-------------|--------|
| auth_check_application | /HEC1/CDAP | Application access: COCKPIT, WORKBASKET, ASSUM_TPL |
| auth_check_configurator | /HEC1/CONF | Edit/display configurator |
| auth_check_edit | /HEC1/CDDE | Edit permission per team_id |
| auth_check_ext_function | /HEC1/CDDH | External functions: CDD_EXCEL, CDD_PDF, CONFIG, LSVIEWER |
| auth_check_ui_function | /HEC1/CDDC | UI functions per team |

Control parameters in /HEC1/CDD_PARAM: CDD_AUTHORIZATION_CHECK (master switch), CDD_AUTHORIZATION_LOG (BAL logging), CDD_AUTHORIZATION_CHECK_BY_ROLE% (role-based variant)

### SAP Roles (11 roles mentioning HEC1/CDD)
GLOB_ERP_GL_DEL_CDD_VIEW, GLOB_ERP_GL_DEL_CR_CDD_PROC, O:ERP:CA_:M:CR_CDD_PROC_:00000, S:ERP:P&P:E:TD_CDDAUTMTN:00000, GLOB_ERP_P&P_TD_CDDAUTMTN_XPRT, GLOB_ERP_P&P_TD_CDDCATGRY_XPRT, 0000_ERP_P&P_TD_CDDAUTMTN_MNTN, 00FG_ERP_PL_OEC_SGS_CDD_ECSS, 0000_ERP_BRM_TU_CDD_DID_LDAP, S:ERP:TU_:M:CDD_DID_LDAP:00000

No T-codes assigned — CDD uses FPM/Fiori UI not classic SAP transactions.

### Auth Domain Values

**CDD_AUTH_APPL (Applications):** COCKPIT, WORKBASKET, ASSUM_TPL
**CDD_AUTH_FUNCTION (Edit Functions):** EDIT, FIN_APPR, REQ_APPR, REJ_EXT, REJ_INT, REJ_PART, SET_FINDAT
**CDD_AUTH_FUNCTION_EXT (External Functions):** ASSUM_TPL, CDD_EXCEL, CDD_PDF, CONFIG, LSVIEWER
**CDD_VIEW_NAME (Views):** ASSUMPTION, CONTACTCUS, CONTACTECS, GENERAL, IBPLOG, OVERVIEW, PPLAN, REMARK, RESOURCE, RISK
`);
sep();

// 11. CDD ↔ Provisioning & Configuration Integration
ln('## CDD Integration: Provisioning & Configuration Cockpit\n');
ln(`
### CDD is Already Confirmed Integrated in Both Areas

**Entry point from Configuration Cockpit:**
- \`/HEC1/CL_CC_GUIBBF_CDD_REQUEST\` (package /HEC1/CP_CONFIGURATION)
  - FPM Form GUIBB popup — triggers CDD request from Config Cockpit
  - Calls \`/HEC1/CL_CDD_MANAGER~create_cdd_request()\`
  - Sets CDD status "03 - CDD requested" via \`/HEC1/CL_STATUS_HANDLER\`
  - Updates BOPF Root node via BOBF service manager

**Exchange table between CDD and Provisioning:**
- \`/HEC1/PROV_CDDEX\` — Key: (CLIENT, BUILD_START_DATE, CONFIG_ID, CONFIG_VERSION, NODE_GUID)
  - Tracks CDD approval milestone (build start date) per configuration node

**IBP Integration tables (/HEC1/CP_CDD_IBP):**
- \`/HEC1/CDD_IBP_RQ\` — Full snapshot (50+ fields): hardware specs, datacenter, DR, SLA dates, ATP status
- \`/HEC1/CDD_IBP_F\` — Filter audit trail with user/timestamp

### Provisioning Package Architecture (/HEC1/CP_PROV*)
11 packages, ~886 objects:
- \`/HEC1/CP_PROV_DDIC\` (356): Core DDIC — 139 tables, 26 domains, 19 CDS views
- \`/HEC1/CP_PROV_OBJECTS\` (90): Domain model — 48 interfaces, 41 classes (IF/CL_PROV_TIER/SOLUTION/LANDSCAPE/DATABASE/APP_SERVER...)
- \`/HEC1/CP_PROVISIONING_V2\` (74): RAP/OData — BDEF I_PROV_LANDSCAPE, OData service PROV_LANDSCAPE
- \`/HEC1/CP_PROV_S2D\` (220): S2D handover — 57 tables (PROV_S2D_SOLUTION/LANDSCAPE/TIER/DB_SRV_INS/AP_SRV_INS), 36 classes, 23 WD components
- \`/HEC1/CP_PROV_CTC\` (47): CTC data
- \`/HEC1/CP_PROV_APP\` (41): Application layer

### Configuration Cockpit (/HEC1/CP_CONFIGURATION — 143 objects)
UI for managing SAP solution configurations and triggering CDD requests.
Key classes: CL_CC_GUIBBF_CDD_REQUEST, CL_CC_MODEL, CL_CC_FACTORY, CL_CC_SEARCH_CTRL, CL_CFG_AUTHENTIFICATOR
Key interfaces: IF_CC_CONSTANTS, IF_CC_FACTORY, IF_CC_MODEL
`);
sep();

// 12. BOPF Package Architecture
ln('## BOPF Package Architecture (/HEC1/BOPF*)\n');
ln(`
33 packages implementing the BOPF business object layer for HEC1 configurations (~1,500+ objects).
Used by Configuration Cockpit. CDD accesses BOPF via /HEC1/CDDBOPFCNF and BOBF service manager.

### Package Summary

| Package | Objects | Key Content |
|---------|---------|-------------|
| \`/HEC1/BOPF_CONFIGURATION\` | 514 | 6 BOBF objects, 308 tables, 148 table types, core classes/interfaces |
| \`/HEC1/BOPF_NODE_HANDLER\` | 117 | 115 node handler classes (one per node type) |
| \`/HEC1/BOPF_COPY_CONTROL\` | 36 | Copy controllers for all CR/Renewal/Restructure scenarios |
| \`/HEC1/BOPF_PRICE_AGGREGATION\` | 88 | Pricing engine: uplift, cost-based, time-based, adjustments |
| \`/HEC1/BOPF_VALIDATION\` | 52 | Node validation classes + 14 validation domains |
| \`/HEC1/BOPF_APM_HANDLING\` | 41 | APM change handling (initial deal + CR variants) |
| \`/HEC1/BOPF_DDIC\` | 202 | BOPF-specific DDIC (103 tables, 76 table types) |
| \`/HEC1/BOPF_LOGGING\` | 32 | Action log + BAL logging (/HEC1/ACT_LOG table) |
| \`/HEC1/BOPF_DR_SYNC\` | 25 | DR tier sync (controller, handler, factory per node type) |
| \`/HEC1/BOPF_PROCESS_NODE\` | 21 | Process node framework |
| \`/HEC1/BOPF_PRICE_DETERMINATION\` | 21 | Price determination per component |
| \`/HEC1/BOPF_CLASS\` | 41 | Base classes: CL_CONFIG_AUTH_CHECK, CL_LIB_A_SUPERCLASS, CL_LIB_D_SUPERCLASS |
| \`/HEC1/BOPF_CLASS_ACTION\` | 14 | Action classes: CL_CONFIG_ACTION, *_APP_SE, *_DB_SE, *_DBO, *_SOM |
| \`/HEC1/BOPF_CLASS_ACTION_HELPER\` | 17 | Action helpers: CL_CONFIG_ACTION_HELPER, *_CR_SOL_HLP, *_DEL_HELPER, *_MOD_HELPER |
| \`/HEC1/BOPF_BUP\` | 11 | BUP: CL_ACTRENEW_CONTROLLER, CL_BUP_CONTROLLER, CL_BUP_HANDLER |
| \`/HEC1/BOPF_API_CONFIG\` | 19 | API configuration handler |

### Key Node Handlers (sample from 115 total)
CL_BOPF_NODE_ROOT, CL_BOPF_NODE_SOLUTION, CL_BOPF_NODE_TIER, CL_BOPF_NODE_TIER_SLA,
CL_BOPF_NODE_DELUNIT, CL_BOPF_NODE_DC, CL_BOPF_NODE_IF_BLINE, CL_BOPF_NODE_NW_SEGM,
CL_BOPF_NODE_CONN, CL_BOPF_NODE_APPSRV, CL_BOPF_NODE_DBSRV, CL_BOPF_NODE_DBSRVIN,
CL_BOPF_NODE_MATERIAL, CL_BOPF_NODE_PHASE, CL_BOPF_NODE_PRICEADJ, CL_BOPF_NODE_CONTACT,
CL_BOPF_NODE_LTB_AM, CL_BOPF_NODE_TLTB, CL_BOPF_NODE_DR_EXCL

### Copy Controller Factory (key for understanding CR types)
\`/HEC1/CL_COPY_CTRL_FACTORY\` selects controller based on operation:
- CREATE_CR → \`CL_COPY_CTRL_CREATE_CR\`
- AUTO_RENEW → \`CL_COPY_CTRL_AUTO_RENEW\` / \`CL_COPY_CTRL_ACTIV_RENEW\`
- TECH_RESTRU → \`CL_COPY_CTRL_TECH_RESTRU\`
- COMM_RESTRU → \`CL_COPY_CTRL_COMM_RESTRU\`
- NEW_VERSION → \`CL_COPY_CTRL_NEW_VERSION\`
- DR_SYNC → \`CL_COPY_CTRL_DR\`
`);
sep();
sep();

ln('## How to Use This Knowledge Base\n');
ln(`
- **"What does X do?"** → Look in "Key Objects by Pattern" or "All Objects by Package"
- **"Who calls interface Y?"** → Check "Interface Dependency Map" or calledBy in the object entry
- **"How does automation work?"** → CL_NCDD_AUTO_FACTORY → calculator by level_id → IF_NCDD_AUTO_CALC.calculate()
- **"How do notifications work?"** → CL_CDD_EMAIL.EVENT_CDD() → feature check → CDD_CUSTOM recipients → BCS
- **Need source code?** → Use \`cdd_local_get_code\` MCP tool with object_name and object_type
- **Search by keyword?** → Use \`cdd_local_search\` MCP tool with a query string
`);

const systemPrompt = lines.join('\n');
fs.writeFileSync(path.join(EXPORTS_DIR, 'cdd-system-prompt.md'), systemPrompt);
console.log(`cdd-system-prompt.md: ${systemPrompt.length.toLocaleString()} chars, ${lines.length} lines`);

// ── RAG chunks (JSONL) ────────────────────────────────────────────────────────

const chunks = [];
for (const rec of records) {
  const text = [
    `Object: ${rec.name}`,
    `Type: ${rec.type}`,
    `Package: ${rec.package}`,
    `Description: ${rec.description || ''}`,
    `Role: ${rec.analysis?.role || ''}`,
    `Patterns: ${(rec.analysis?.patterns || []).join(', ')}`,
    `Key methods: ${(rec.analysis?.keyMethods || []).join(', ')}`,
    `Dependencies: ${(rec.analysis?.dependencies || []).join(', ')}`,
    `Tables: ${(rec.analysis?.tables || []).join(', ')}`,
    `Summary: ${rec.analysis?.summary || ''}`,
    rec.source ? `Source excerpt: ${rec.source.slice(0, 500)}` : '',
  ].filter(Boolean).join('\n');

  chunks.push(JSON.stringify({
    id: rec.name,
    type: rec.type,
    package: rec.package,
    patterns: rec.analysis?.patterns || [],
    text,
  }));
}

fs.writeFileSync(path.join(EXPORTS_DIR, 'cdd-rag-chunks.jsonl'), chunks.join('\n'));
console.log(`cdd-rag-chunks.jsonl: ${chunks.length} chunks`);

console.log('\nExport build complete.');
console.log(`System prompt: ${path.join(EXPORTS_DIR, 'cdd-system-prompt.md')}`);
