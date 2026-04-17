#!/usr/bin/env node
/**
 * Graph builder — reads all knowledge/abap/ JSON files and produces:
 *   knowledge/graph/index.json     — node registry
 *   knowledge/graph/edges.json     — dependency edges
 *   knowledge/graph/mind-map.json  — package hierarchy tree
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const config        = JSON.parse(fs.readFileSync(path.join(__dirname, 'pipeline-config.json'), 'utf8'));
const KNOWLEDGE_DIR = path.resolve(__dirname, config.knowledgeDir);
const ABAP_DIR      = path.join(KNOWLEDGE_DIR, 'abap');
const GRAPH_DIR     = path.join(KNOWLEDGE_DIR, 'graph');

const TYPE_DIR = {
  class: 'classes',
  interface: 'interfaces',
  program: 'programs',
  function_group: 'function-groups',
  cds_view: 'cds-views',
  behavior_definition: 'behavior-definitions',
};

// Ensure output dir
if (!fs.existsSync(GRAPH_DIR)) fs.mkdirSync(GRAPH_DIR, { recursive: true });

// Load all JSON records from disk
function loadAllRecords() {
  const records = [];
  for (const [type, dir] of Object.entries(TYPE_DIR)) {
    const dirPath = path.join(ABAP_DIR, dir);
    if (!fs.existsSync(dirPath)) continue;
    for (const f of fs.readdirSync(dirPath)) {
      if (!f.endsWith('.json')) continue;
      try {
        const rec = JSON.parse(fs.readFileSync(path.join(dirPath, f), 'utf8'));
        records.push(rec);
      } catch (e) { console.warn(`Skipping malformed ${f}`); }
    }
  }
  return records;
}

console.log('Loading records...');
const records = loadAllRecords();
console.log(`Loaded ${records.length} records.`);

// ── index.json — node registry ───────────────────────────────────────────────

const index = {};
for (const rec of records) {
  index[rec.name] = {
    name: rec.name,
    type: rec.type,
    package: rec.package,
    description: rec.description || '',
    role: rec.analysis?.role || '',
    patterns: rec.analysis?.patterns || [],
    keyMethods: rec.analysis?.keyMethods || [],
    lines: rec.lines || 0,
    sizeBytes: rec.sizeBytes || 0,
    calledBy: rec.calledBy || [],
    crawledAt: rec.crawledAt,
  };
}

fs.writeFileSync(path.join(GRAPH_DIR, 'index.json'), JSON.stringify(index, null, 2));
console.log(`index.json: ${Object.keys(index).length} nodes`);

// ── edges.json — dependency edges ────────────────────────────────────────────

const edges = [];
for (const rec of records) {
  // Forward edges from dependencies
  for (const dep of (rec.analysis?.dependencies || [])) {
    if (dep && dep !== rec.name) {
      edges.push({ from: rec.name, to: dep, type: 'depends-on' });
    }
  }
  // Reverse edges from calledBy
  for (const caller of (rec.calledBy || [])) {
    if (caller && caller !== rec.name) {
      edges.push({ from: caller, to: rec.name, type: 'calls' });
    }
  }
}

// Deduplicate
const edgeSet = new Set();
const uniqueEdges = edges.filter(e => {
  const key = `${e.from}→${e.to}:${e.type}`;
  if (edgeSet.has(key)) return false;
  edgeSet.add(key);
  return true;
});

fs.writeFileSync(path.join(GRAPH_DIR, 'edges.json'), JSON.stringify(uniqueEdges, null, 2));
console.log(`edges.json: ${uniqueEdges.length} edges`);

// ── mind-map.json — package hierarchy tree ───────────────────────────────────

const tree = {};
for (const rec of records) {
  const pkg = rec.package || 'UNKNOWN';

  // Build package path: /HEC1/CP_CDD/CP_CDD_APP → ["HEC1", "CP_CDD", "CP_CDD_APP"]
  // We'll use a flat map: package → list of objects
  if (!tree[pkg]) tree[pkg] = { package: pkg, children: [], objects: [] };
  tree[pkg].objects.push({
    name: rec.name,
    type: rec.type,
    role: rec.analysis?.role || '',
    patterns: rec.analysis?.patterns || [],
  });
}

// Compute package summary stats
for (const pkg of Object.values(tree)) {
  pkg.objectCount = pkg.objects.length;
  const byType = {};
  for (const o of pkg.objects) byType[o.type] = (byType[o.type] || 0) + 1;
  pkg.byType = byType;
}

// Build root-level hierarchy
const rootPackages = config.rootPackages;
const mindMap = {
  roots: rootPackages,
  packages: tree,
  summary: {
    totalObjects: records.length,
    byType: Object.fromEntries(
      Object.entries(TYPE_DIR).map(([type]) => [
        type,
        records.filter(r => r.type === type).length
      ])
    ),
    generatedAt: new Date().toISOString(),
  },
};

fs.writeFileSync(path.join(GRAPH_DIR, 'mind-map.json'), JSON.stringify(mindMap, null, 2));
console.log(`mind-map.json: ${Object.keys(tree).length} packages`);

// ── Top 20 most-connected nodes (for export builder) ─────────────────────────

const connectivity = {};
for (const e of uniqueEdges) {
  connectivity[e.from] = (connectivity[e.from] || 0) + 1;
  connectivity[e.to]   = (connectivity[e.to]   || 0) + 1;
}
const top20 = Object.entries(connectivity)
  .sort((a, b) => b[1] - a[1])
  .slice(0, 20)
  .map(([name, degree]) => ({ name, degree, ...(index[name] || {}) }));

fs.writeFileSync(path.join(GRAPH_DIR, 'top-connected.json'), JSON.stringify(top20, null, 2));
console.log(`top-connected.json: top ${top20.length} nodes`);

console.log('\nGraph build complete.');
