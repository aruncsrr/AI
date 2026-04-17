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

// Embedded CDD/NCDD domain knowledge (always included)
ln('## CDD/NCDD Domain Knowledge (Embedded)\n');
ln(`
### Notification System (CDD v1)
- **Main class:** \`/HEC1/CL_CDD_EMAIL\` — orchestrates all email notifications
- **Events:** requested, send_to_approve, dr_requested, rejected_int_gdo, rejected_int, approved, maintain_subcontractor
- **Flow:** EVENT_CDD(event_id) → is_feature_enabled() → recipients from /HEC1/CDD_CUSTOM → CL_BCS send → log /HEC1/CDD_ACTION

### Feature Toggle Mechanism
- No dedicated toggle table
- All gating via \`/HEC1/CL_PROV_UTILITY=>is_feature_enabled(iv_feature_id)\`
- Known IDs: CDD_NOTIF_26285, CDD_NOTIF_33189, CDD_NOTIF_26257

### Customization Tables
- \`/HEC1/CDD_CUSTOM\` — generic params + email recipients (key1/key2/key3)
- \`/HEC1/CDDBOPFCNF\` — BOPF field mapping
- \`/HEC1/CDD_CR\` — CR customization (lead times, durations)
- \`/HEC1/CDD_ACTION\` — audit log (all state changes + notifications)
- \`/HEC1/NCDD_AUTO_S_CALC_CUST\` — automation calculator configuration

### CDD v1 vs CDD 2.0
| Aspect | CDD v1 | CDD 2.0 (NCDD) |
|--------|--------|----------------|
| Framework | BOPF | RAP |
| UI | Web Dynpro | Fiori Elements |
| Entry point | CL_CDD_MANAGER.get_instance() | BPCL_I_NCDD_WORKLIST |
| Automation | Manual | Factory + calculators |
| Draft | No | Yes |
`);
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
