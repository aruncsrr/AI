#!/usr/bin/env node
/**
 * Bulk offline downloader — fetches source for all 503 ABAP objects
 * from SAP ISD via ADT REST and stores them in knowledge/abap/
 *
 * Run: node pipeline/bulk-download.js [--concurrency 4] [--resume]
 */

import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dir = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dir, '..');
const OBJECTS_FILE = '/tmp/abap_objects.json';
const STATE_FILE = join(ROOT, 'knowledge/exports/download-state.json');
const MCP_BASE = 'http://localhost:8080';

const CONCURRENCY = parseInt(process.argv.find(a => a.startsWith('--concurrency='))?.split('=')[1] || '4');
const RESUME = process.argv.includes('--resume');

// ─── State ─────────────────────────────────────────────────────────────────
function loadState() {
  if (RESUME && existsSync(STATE_FILE)) {
    return JSON.parse(readFileSync(STATE_FILE, 'utf8'));
  }
  return { done: {}, errors: {} };
}
function saveState(state) {
  mkdirSync(dirname(STATE_FILE), { recursive: true });
  writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));
}

// ─── MCP call ──────────────────────────────────────────────────────────────
async function mcpCall(tool, params) {
  const resp = await fetch(`${MCP_BASE}/api/tools/${tool}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params)
  });
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  return resp.json();
}

// ─── Per-type fetchers ─────────────────────────────────────────────────────
const TOOL_MAP = {
  class:          name => mcpCall('GetClass',          { object_name: name }),
  interface:      name => mcpCall('GetInterface',      { object_name: name }),
  program:        name => mcpCall('GetProgram',        { object_name: name }),
  function_group: name => mcpCall('GetFunctionGroup',  { object_name: name }),
};

// ─── File writer ───────────────────────────────────────────────────────────
const DIR_MAP = {
  class:          'classes',
  interface:      'interfaces',
  program:        'programs',
  function_group: 'function_groups',
};

function writeSource(type, name, pkg, desc, data) {
  const dir = join(ROOT, 'knowledge/abap', DIR_MAP[type]);
  mkdirSync(dir, { recursive: true });
  const safeName = name.replace(/\//g, '_').replace(/[^a-zA-Z0-9_-]/g, '');
  const file = join(dir, safeName + '.abap');

  // Extract source from MCP response
  let source = '';
  if (data.source) source = data.source;
  else if (data.content && Array.isArray(data.content)) {
    // MCP tool_result format: content[].text contains the file path line
    const text = data.content.map(c => c.text || '').join('\n');
    // Response says "File: /tmp/sap-mcp/..." — read it
    const fileMatch = text.match(/File:\s*(\S+\.abap)/);
    if (fileMatch && existsSync(fileMatch[1])) {
      source = readFileSync(fileMatch[1], 'utf8');
    } else {
      source = text; // fallback: store the summary
    }
  }

  const header = `" Object:      ${name}
" Type:        ${type}
" Package:     /HEC1/${pkg}
" Description: ${desc || '(none)'}
" Downloaded:  ${new Date().toISOString()}
" ─────────────────────────────────────────────────────────────────────────────

`;
  writeFileSync(file, header + source);
  return file;
}

// ─── Concurrency helper ────────────────────────────────────────────────────
async function runPool(tasks, concurrency, fn) {
  const results = [];
  let i = 0;
  async function worker() {
    while (i < tasks.length) {
      const idx = i++;
      results[idx] = await fn(tasks[idx], idx);
    }
  }
  await Promise.all(Array.from({ length: concurrency }, worker));
  return results;
}

// ─── Main ──────────────────────────────────────────────────────────────────
async function main() {
  // Check MCP server
  try {
    await fetch(`${MCP_BASE}/health`);
  } catch {
    console.error('✗ SAP MCP server not reachable at', MCP_BASE);
    console.error('  Start: bash /Users/I766689/Desktop/start-sap-assistant.sh');
    process.exit(1);
  }

  const objects = JSON.parse(readFileSync(OBJECTS_FILE, 'utf8'));
  const state = loadState();

  const allTasks = [
    ...objects.classes.map(o => ({ ...o, type: 'class' })),
    ...objects.interfaces.map(o => ({ ...o, type: 'interface' })),
    ...objects.programs.map(o => ({ ...o, type: 'program' })),
    ...objects.function_groups.map(o => ({ ...o, type: 'function_group' })),
  ];

  const pending = RESUME
    ? allTasks.filter(t => !state.done[t.name] && !state.errors[t.name])
    : allTasks;

  console.log(`\nBulk Download — ${pending.length} objects (concurrency=${CONCURRENCY})`);
  console.log(`Already done: ${Object.keys(state.done).length}, errors: ${Object.keys(state.errors).length}\n`);

  const stats = { ok: 0, err: 0, skip: 0 };
  let processed = 0;

  await runPool(pending, CONCURRENCY, async (obj) => {
    const key = obj.name;
    try {
      const fetcher = TOOL_MAP[obj.type];
      const data = await fetcher(obj.name);
      const file = writeSource(obj.type, obj.name, obj.pkg, obj.desc, data);
      state.done[key] = { file, ts: new Date().toISOString() };
      stats.ok++;
    } catch (e) {
      state.errors[key] = e.message;
      stats.err++;
      console.error(`  ✗ ${obj.name}: ${e.message}`);
    }
    processed++;
    if (processed % 20 === 0 || processed === pending.length) {
      process.stdout.write(`\r  Progress: ${processed}/${pending.length} (ok=${stats.ok} err=${stats.err})`);
      saveState(state);
    }
  });

  saveState(state);
  console.log(`\n\nDone! ok=${stats.ok} errors=${stats.err}`);

  if (stats.ok > 0) {
    console.log('\nRebuilding search index...');
    const { execSync } = await import('child_process');
    execSync('node pipeline/build-search-index.js', { stdio: 'inherit', cwd: ROOT });
    console.log('✓ Index rebuilt');
  }
}

main().catch(e => { console.error(e); process.exit(1); });
