# Knowledge Base: /HEC1/CP_CDD and /HEC1/CP_NCDD

## Package Hierarchy

### /HEC1/CP_CDD — CDD v1 (Legacy, BOPF-based)

| Sub-package | Description | Key objects |
|---|---|---|
| `/HEC1/CP_CDD_APP` | Application Layer | 26 classes, 5 interfaces, 28 programs, 2 FGs |
| `/HEC1/CP_CDD_DDIC` | Dictionary Objects | 27 DB tables, 54 structures, 107 DEs, 8 FGs, 6 CDS views |
| `/HEC1/CP_CDD_IBP` | IBP Integration | 14 classes, 3 interfaces, 14 programs |
| `/HEC1/CP_CDD_UI` | Web Dynpro UI | 43 classes, UI components |
| `/HEC1/CP_CDD_REPORTING` | Reporting | 9 CDS views, OData/RAP services |
| `/HEC1/CP_CDD_ODATA` | OData (IW) | 6 objects: IW OData models |
| `/HEC1/CP_CDD_CONFIG` | Configuration | Includes sub-package `/HEC1/CP_CDD_CONFIG_GDO_RL` |
| `/HEC1/CP_CDD_OBSOLETE` | Obsolete | 18 deprecated classes |
| `/HEC1/CP_NCDD` | CDD 2.0 root | Sub-package of /HEC1/CP_CDD |

### /HEC1/CP_NCDD — CDD 2.0 (RAP-based)

| Sub-package | Description | Key objects |
|---|---|---|
| `/HEC1/CP_NCDD_APP` | Application Layer | 55 classes, 11 FGs — core business logic |
| `/HEC1/CP_NCDD_AUTO` | Automation Engine | 24 classes, 5 interfaces, 13 CDS views |
| `/HEC1/CP_NCDD_CDS` | RAP Layer | 13 classes, 10 CDS views, 3 BDEFs |
| `/HEC1/CP_NCDD_DDIC` | Dictionary | 45 DB tables, 3 classes, 11 FGs |
| `/HEC1/CP_NCDD_TEMP` | Next-gen RAP (N-suffix) | 9 classes, 25 CDS views, 3 BDEFs |
| `/HEC1/CP_NCDD_BUSINESS_SERVICE` | OData/RAP Services | 16 objects |
| `/HEC1/NCDD_BUSINESS_SERVICES` | Business Services | 5 classes, IW OData models |
| `/HEC1/CP_NCDD_WORKLIST_BSP` | Worklist BSP App | 5 objects |

---

## CDD v1 Architecture

### Core Pattern: Singleton Manager
- Entry point: `CL_CDD_MANAGER.get_instance(config_key)` → returns `IF_CDD_MANAGER`
- Implements singleton + lazy load of full config entity graph
- `IF_CDD_MANAGER` defines all operations: create, update, submit, approve, reject, copy

### Key Interfaces
- `IF_CDD_MANAGER` — central facade for all CDD operations
- `IF_CDD_CONSTANTS` — central type repository (100+ types, structures, enums)
- `IF_CDD_STATUS` — status constants (DRAFT, SUBMITTED, APPROVED, REJECTED, etc.)
- `IF_CDD_AUTH` — authorization interface

### Key Classes
| Class | Role |
|---|---|
| `CL_CDD_MANAGER` | Main facade; implements `IF_CDD_MANAGER`; singleton per config key |
| `CL_CDD_CONFIG_BUFFER` | Caches all 30+ entity types lazily; main performance layer |
| `CL_CDD_BOPF_UPDATE` | Syncs CDD state to BOPF business objects |
| `CL_CDD_AUTH_CHECK` | Static authorization guard (activity-based: 01=Create, 02=Change, etc.) |
| `CL_CDD_VERSION_COMPARE` | Field-level diff between two CDD versions; returns changed field list |
| `CL_CDD_STATUS_MACHINE` | Validates legal status transitions |
| `CL_CDD_COPY` | Deep-copies CDD with all sub-entities |
| `CL_CDD_VALIDATOR` | Business rule validation before status changes |
| `CL_CDD_NOTIFICATION` | Sends SAP workflow notifications on status changes |
| `CL_CDD_PDF_OUTPUT` | Generates PDF output of CDD |
| `CL_CDD_IBP_PROC_INBOUND` | Polls OData from SAP IBP; parses XML/JSON hardware build plans |
| `CL_CDD_IBP_PARSER` | XML/JSON parser for IBP payload |

### CDD v1 Data Model (Key Tables)
- `/HEC1/CDD_HEAD` — CDD header (CONFID, VERSION, STATUS, CREATED_BY, etc.)
- `CDD_PPLAN` — Project plan lines (milestones, dates)
- `CDD_RESULT` — Delivery results/findings
- `CDD_TEAM` — Team members assigned to CDD
- `CDD_CR` — Change requests linked to CDD
- `CDD_ASSUM` — Assumptions
- `CDD_PARAM` — Configuration parameters

### /HEC1/CDD_HEAD Key Fields
| Field | Description |
|---|---|
| `hec_confid` | Configuration ID (12 char, Key) |
| `hec_conf_version` | Config version (Key) |
| `cdd_version` | CDD version (Key) |
| `request_type` | CDD request type (INITIAL_DEAL, CR_OUTCOME, etc.) |
| `cdd_status` | Approval status (numeric 00-99) |
| `xteam_gdo` | Primary GDO team indicator (notification routing) |
| `xteam_dr` | DR GDO team indicator |
| `xteam_cr` | CR team indicator |
| `current_team` | Current team ID (numeric) |
| `xdr_approval_needed` | DR approval required flag |
| `xibp_hw_req_created` | IBP HW request created flag |
| `expected_s2d_date` | Expected S2D handover date |
| `expected_del_date` | Expected delivery date |
| `committed_del_date` | Committed delivery date |
| `planned_signature_date` | Planned signature date |
| `cdd_expired_date` | CDD expiration date |
| `cr_counter` | Change request counter |
| `subcon_code` | Subcontractor code |
| `cdd_lco` | Large Customer Operations flag |
| `processing_team` | Processing team |

---

## CDD 2.0 (NCDD) Architecture

### Core Pattern: RAP (ABAP RESTful Application Programming)
- Behavior Definition: `/HEC1/I_NCDD_WORKLIST` (interface/root) — draft-enabled, 13 events
- Consumption: `/HEC1/C_NCDD_WORKLIST` (projection/consumption BDEF)
- Draft persistence: `/HEC1/NCDD_WORKD`
- Active persistence: `/HEC1/NCDD_HEAD`
- UI: Fiori Elements based on CDS consumption view `C_NCDD_WORKLIST`

### RAP Layer Objects
| Object | Type | Role |
|---|---|---|
| `/HEC1/I_NCDD_WORKLIST` | BDEF | Interface behavior; 13 events; draft-enabled |
| `/HEC1/C_NCDD_WORKLIST` | BDEF | Consumption/projection behavior |
| `BPCL_I_NCDD_WORKLIST` | Class | Root behavior implementation |
| `BPCL_I_NCDD_GEN_INFO` | Class | General info node behavior |
| `BPCL_I_NCDD_PROJPLAN` | Class | Project plan node behavior |
| `BPCL_I_NCDD_RISKS` | Class | Risks node behavior |
| `BPCL_I_NCDD_ASSUM` | Class | Assumptions node behavior |
| `CL_NCDD_EV_BOPF_UPD_HNDL` | Class | RAP event → BOPF bridge |

### CDD 2.0 Data Model
- `/HEC1/NCDD_HEAD` — Header (root entity); extends v1 with: `xauto_approve`, `xregsrv`, `xregsrv_impacted`
- `/HEC1/NCDD_WORKD` — Draft table (RAP draft persistence)
- `/HEC1/NCDD_PPLAN` — Project plan
- `/HEC1/NCDD_ASSUM` — Assumptions
- `/HEC1/NCDD_RISK` — Risks
- `/HEC1/NCDD_RESPL` — Resource planning
- 45 total DB tables in `/HEC1/CP_NCDD_DDIC`

### Automation Framework (CP_NCDD_AUTO)
Factory + polymorphic calculator pattern:
- `CL_NCDD_AUTO_FACTORY` — creates calculator instances by level_id
- `IF_NCDD_AUTO_CALC` — interface all calculators implement (method: `calculate()`)
- `CL_NCDD_AUTO_LOGGER` — JSON-serialized audit trail for all calculations

**Automation level_ids (calculator variants):**
| Level ID | Meaning |
|---|---|
| SRV | Service |
| TIE | Time-in-effect |
| SLA | SLA-based |
| LTB | Long-term budget |
| CON | Contract |
| ADA | Adapter A |
| ADB | Adapter B |
| DCT | Document/contract |
| DBI | DB integration |
| DPC | DPC target |
| DBS | DB summary |

---

## Notification System

### CDD v1 Notifications
**Main class:** `/HEC1/CL_CDD_EMAIL` (1,407 lines) in `/HEC1/CP_CDD_APP`
- Implements `IF_CDD_EMAIL` interface
- Uses SAP BCS (Business Communication Services) for delivery
- HTML templates loaded from MIME repository

**Supported Email Events (gc_business_event_id constants):**
| Event ID | Trigger |
|---|---|
| `requested` | Initial CDD request submitted |
| `send_to_approve` | CDD forwarded to approvers |
| `dr_requested` | Disaster Recovery approval needed |
| `rejected_int_gdo` | GDO team rejected internally |
| `rejected_int` | Internal rejection |
| `approved` | CDD approved |
| `maintain_subcontractor` | Subcontractor maintenance |

### Notification Flow
```
Event trigger (iv_event_id)
  → Feature check: /HEC1/CL_PROV_UTILITY=>is_feature_enabled(feature_id)
  → Load config data via /HEC1/IF_DATA_PROVIDER
  → Determine recipients from /HEC1/CDD_CUSTOM (key1='email', key2=role, key3=type)
  → Generate subject from template + variable substitution
  → Load HTML template from MIME repository
  → Send via CL_BCS (SAP Business Communication Services)
  → Log to /HEC1/CDD_ACTION table
```

---

## Feature Toggle Mechanism

**No dedicated toggle table** — feature gating uses `/HEC1/CL_PROV_UTILITY=>is_feature_enabled(iv_feature_id)`.

### Known Feature IDs
| Feature ID | Purpose |
|---|---|
| `CDD_NOTIF_26285` | GDO team rejection notification |
| `CDD_NOTIF_33189` | Initial request notification |
| `CDD_NOTIF_26257` | Send-to-approve notification |

Feature IDs map to JIRA ticket numbers. When a feature is gated, the code checks `is_feature_enabled` before executing the notification branch.

---

## Customization Tables

### /HEC1/CDD_CUSTOM — Central Customization Parameter Table
| Field | Description |
|---|---|
| `key1` | First key (e.g., `email`) |
| `key2` | Second key (event/role: `cr_team`, `gdo_team`, `dr_team`, `approver_team`, `approver_cc`, `support_region`) |
| `key3` | Third key (recipient type: `SEND_TO`, `CC`, `SUBJECT`) |
| `lfdnr` | Sequence number |
| `sysid` | System ID (for system-specific overrides) |
| `value_c1` | Character value 1 (email address or template name) |
| `value_c2` | Character value 2 (variable reference) |
| `description` | Parameter description |
| `email_subject_init` | Email subject for initial deals |
| `email_subject_cr` | Email subject for change requests |

Accessed via `/HEC1/CDD_CUSTOMIZE`: `GET_SINGLE(key1, key2, key3)` and `GET_MULTI()`.

### /HEC1/CDDBOPFCNF — BOPF Field Mapping Configuration
Used to map BOPF node fields to configuration structures.
Accessed by `CL_CDD_READ_CONFIG_DATA` (v1) and `CL_NCDD_READ_CONFIG_DATA` (v2).

### /HEC1/CDD_CR — Change Request Customization
Contains: `conf_obj_type`, `mod_type`, `team_id`, `request_type`, `hec_min_lead_time`, `hec_prep_duration`, `hec_exec_duration`, `hec_post_duration`.
Accessed via `/HEC1/CL_CDD_CR_CUSTOMIZING`.

### /HEC1/CDD_ACTION — Actions Audit Log
Records: `hec_confid`, `hec_conf_version`, `cdd_version`, `timestampl`, `uname`, `action_type`, `action_code`, `action_text`.
All notification sends, status changes, and CDD actions are logged here.

### NCDD Automation Customization
- `/HEC1/NCDD_AUTO_S_CALC_CUST` — `days_before_start`, `days_duration`, `days_duration_add`
- `/HEC1/NCDD_STLOG` — Log table for item status changes

---

## CDD v1 vs CDD 2.0 Comparison

| Aspect | CDD v1 | CDD 2.0 (NCDD) |
|---|---|---|
| Framework | BOPF | RAP (ABAP RESTful) |
| UI | Web Dynpro ABAP | Fiori Elements |
| API | Internal BOPF API | OData V4 + RAP |
| Draft | No | Yes (RAP draft) |
| Events | Workflow notifications | 13 RAP events |
| Automation | Manual | Factory + polymorphic calculators |
| Entry point | `CL_CDD_MANAGER.get_instance()` | `BPCL_I_NCDD_WORKLIST` |

---

## Key Cross-Package Relationships

1. `CL_NCDD_EV_BOPF_UPD_HNDL` bridges NCDD RAP events → CDD v1 BOPF objects
2. IBP integration exists in both: `CL_CDD_IBP_*` (v1) and equivalent in NCDD_APP
3. `IF_CDD_CONSTANTS` is referenced by both CDD v1 and NCDD code
4. NCDD_TEMP package (`_N` suffix objects) represents next evolutionary step beyond current NCDD

---

## Q&A Quick Reference

- **Status changes** → `CL_CDD_STATUS_MACHINE` (v1) or BDEF actions in `I_NCDD_WORKLIST` (v2)
- **Automation** → `CL_NCDD_AUTO_FACTORY` → calculator for level_id → `IF_NCDD_AUTO_CALC.calculate()`
- **Tables** → v1: `/HEC1/CDD_HEAD` + CDD_* tables; v2: `/HEC1/NCDD_HEAD` + NCDD_* tables
- **IBP integration** → `CL_CDD_IBP_PROC_INBOUND` polls IBP OData, `CL_CDD_IBP_PARSER` parses
- **RAP BDEF** → `I_NCDD_WORKLIST` (interface) + `C_NCDD_WORKLIST` (consumption); draft-enabled; 13 events
- **Notifications** → `CL_CDD_EMAIL.EVENT_CDD()` → feature check → recipients from `/HEC1/CDD_CUSTOM` → BCS → log `/HEC1/CDD_ACTION`
- **Feature toggles** → `CL_PROV_UTILITY=>is_feature_enabled(iv_feature_id)` — no toggle table
- **Email recipients** → `/HEC1/CDD_CUSTOM` with key1=`email`, key2=role, key3=type (SEND_TO/CC/SUBJECT)
- **Email templates** → MIME repository; subjects in `/HEC1/CDD_CUSTOM.email_subject_init` and `email_subject_cr`
