#!/usr/bin/env node
/**
 * CDD/NCDD Knowledge Pipeline Crawler
 *
 * Phase 1 — Package expansion (BFS): discovers all sub-packages and objects
 * Phase 2 — Object crawl + code-based analysis: fetches source and analyses locally
 * Phase 3 — Where-used: finds callers of interfaces and key classes
 *
 * Uses the Java MCP server at http://localhost:8080 for SNC-authenticated SAP calls.
 * No Claude/internet/proxy required at runtime — SAP access only.
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const config        = JSON.parse(fs.readFileSync(path.join(__dirname, 'pipeline-config.json'), 'utf8'));
const JAVA_URL      = config.javaServerUrl || 'http://localhost:8080';
const SYSTEM_ID     = config.systemId;
const KNOWLEDGE_DIR = path.resolve(__dirname, config.knowledgeDir);
const CRAWL_DIR     = path.join(KNOWLEDGE_DIR, 'crawl');
const ABAP_DIR      = path.join(KNOWLEDGE_DIR, 'abap');

const TYPE_DIR = {
  class:               'classes',
  interface:           'interfaces',
  program:             'programs',
  function_group:      'function-groups',
  cds_view:            'cds-views',
  behavior_definition: 'behavior-definitions',
};

const MANIFEST_FILE = path.join(CRAWL_DIR, 'manifest.json');
const LOCK_FILE     = path.join(CRAWL_DIR, 'crawl.lock');

// ── CLI args ──────────────────────────────────────────────────────────────────

const args = process.argv.slice(2);
const phase       = parseInt(args[args.indexOf('--phase') + 1] || '0', 10);
const concurrency = parseInt(args[args.indexOf('--concurrency') + 1] || String(config.maxConcurrency || 5), 10);
const batchSize   = parseInt(args[args.indexOf('--batch') + 1] || String(config.batchSize || 6), 10);
const limit       = parseInt(args[args.indexOf('--limit') + 1] || '0', 10);

if (!phase || phase < 1 || phase > 3) {
  console.error('Usage: node cdd-crawler.js --phase <1|2|3> [--concurrency N] [--batch N] [--limit N]');
  process.exit(1);
}

// ── Ensure directories ────────────────────────────────────────────────────────

fs.mkdirSync(CRAWL_DIR, { recursive: true });
for (const dir of Object.values(TYPE_DIR)) {
  fs.mkdirSync(path.join(ABAP_DIR, dir), { recursive: true });
}

// ── Crawl lock (PID file) ─────────────────────────────────────────────────────

function acquireLock() {
  if (fs.existsSync(LOCK_FILE)) {
    const pid = fs.readFileSync(LOCK_FILE, 'utf8').trim();
    console.error(`Another crawl is running (PID ${pid}). Delete ${LOCK_FILE} to force.`);
    process.exit(1);
  }
  fs.writeFileSync(LOCK_FILE, String(process.pid));
  process.on('exit', () => { try { fs.unlinkSync(LOCK_FILE); } catch {} });
  process.on('SIGINT',  () => process.exit(1));
  process.on('SIGTERM', () => process.exit(1));
}

// ── Manifest helpers ──────────────────────────────────────────────────────────

function loadManifest() {
  if (!fs.existsSync(MANIFEST_FILE)) {
    return {
      phase1Complete:  false,
      packageQueue:    [...config.rootPackages],
      visitedPackages: [],
      objectQueue:     [],
      crawled:         {},
    };
  }
  return JSON.parse(fs.readFileSync(MANIFEST_FILE, 'utf8'));
}

// Async mutex for manifest writes
let manifestBusy = false;
const manifestQueue = [];

async function withManifestLock(fn) {
  return new Promise((resolve, reject) => {
    manifestQueue.push({ fn, resolve, reject });
    drainManifestQueue();
  });
}

function drainManifestQueue() {
  if (manifestBusy || manifestQueue.length === 0) return;
  manifestBusy = true;
  const { fn, resolve, reject } = manifestQueue.shift();
  Promise.resolve().then(fn).then(
    result => { manifestBusy = false; resolve(result); drainManifestQueue(); },
    err    => { manifestBusy = false; reject(err);    drainManifestQueue(); }
  );
}

function saveManifest(m) {
  fs.writeFileSync(MANIFEST_FILE, JSON.stringify(m, null, 2));
}

// ── MCP STDIO transport (spawns the same Java server Claude Code uses) ────────

import { spawn } from 'child_process';

// Read MCP server config from Claude's config file
function getMcpServerConfig() {
  const claudeConfig = JSON.parse(
    fs.readFileSync(path.join(process.env.HOME, '.claude.json'), 'utf8')
  );
  const srv = claudeConfig?.mcpServers?.['sap-adt'];
  if (!srv) throw new Error('sap-adt MCP server not found in ~/.claude.json');
  return srv;
}

// Persistent MCP process with a request queue — one process, sequential calls.
class McpProcess {
  constructor() {
    this.proc      = null;
    this.buf       = '';
    this.nextId    = 1;
    this.pending   = new Map(); // id → { resolve, reject }
    this.ready     = false;
    this.queue     = [];
    this.draining  = false;
  }

  start() {
    const srv  = getMcpServerConfig();
    this.proc  = spawn(srv.command, srv.args, {
      env:   { ...process.env, ...srv.env },
      stdio: ['pipe', 'pipe', 'pipe'],
    });

    this.proc.stdout.on('data', (chunk) => {
      this.buf += chunk.toString();
      const lines = this.buf.split('\n');
      this.buf = lines.pop();
      for (const line of lines) {
        if (!line.trim()) continue;
        let msg;
        try { msg = JSON.parse(line); } catch { continue; }
        if (msg.id === 0 && msg.result !== undefined) {
          // initialized
          this._send({ jsonrpc: '2.0', method: 'notifications/initialized', params: {} });
          this.ready = true;
          this._drain();
          continue;
        }
        const cb = this.pending.get(msg.id);
        if (cb) {
          this.pending.delete(msg.id);
          cb.resolve(msg);
        }
      }
    });

    this.proc.stderr.on('data', () => {});
    this.proc.on('exit', () => { this.ready = false; });

    // MCP handshake
    this._send({ jsonrpc: '2.0', id: 0, method: 'initialize', params: {
      protocolVersion: '2024-11-05',
      capabilities: {},
      clientInfo: { name: 'cdd-crawler', version: '1.0' }
    }});
  }

  _send(msg) {
    this.proc.stdin.write(JSON.stringify(msg) + '\n');
  }

  call(toolName, input) {
    return new Promise((resolve, reject) => {
      this.queue.push({ toolName, input, resolve, reject });
      this._drain();
    });
  }

  _drain() {
    if (!this.ready || this.draining || this.queue.length === 0) return;
    this.draining = true;
    const { toolName, input, resolve, reject } = this.queue.shift();
    const id = this.nextId++;
    this.pending.set(id, {
      resolve: (msg) => {
        this.draining = false;
        this._drain();
        if (msg.error) {
          resolve({ isError: true, text: msg.error.message || JSON.stringify(msg.error), filePath: '', fileContent: '' });
        } else {
          const content   = msg.result?.content || [];
          const textPart  = content.find(c => c.type === 'text')?.text || '';
          const fileMatch = textPart.match(/File:\s*(\/[^\n\r]+)/);
          const filePath  = fileMatch ? fileMatch[1].trim() : '';
          let fileContent = '';
          if (filePath && fs.existsSync(filePath)) {
            fileContent = fs.readFileSync(filePath, 'utf8');
          }
          resolve({ isError: msg.result?.isError || false, text: textPart, filePath, fileContent });
        }
      },
      reject: (err) => { this.draining = false; this._drain(); reject(err); }
    });
    this._send({ jsonrpc: '2.0', id, method: 'tools/call', params: { name: toolName, arguments: input } });
  }

  stop() {
    try { this.proc?.kill(); } catch {}
  }
}

// Pool of N persistent MCP processes for concurrent calls
class McpPool {
  constructor(size) {
    this.workers = Array.from({ length: size }, () => { const p = new McpProcess(); p.start(); return p; });
    this.idx     = 0;
  }
  call(toolName, input) {
    const worker = this.workers[this.idx % this.workers.length];
    this.idx++;
    return worker.call(toolName, input);
  }
  stop() { this.workers.forEach(w => w.stop()); }
}

let mcpPool = null;

function getPool() {
  if (!mcpPool) {
    mcpPool = new McpPool(concurrency);
  }
  return mcpPool;
}

async function callTool(toolName, input) {
  return getPool().call(toolName, input);
}

async function checkServerHealth() {
  try {
    getMcpServerConfig();
    return true;
  } catch {
    return false;
  }
}

// ── ABAP type mapping ─────────────────────────────────────────────────────────

const ABAP_TYPE_MAP = {
  CLAS: 'class',
  INTF: 'interface',
  PROG: 'program',
  FUGR: 'function_group',
  DDLS: 'cds_view',
  DF:   'cds_view',
  BDEF: 'behavior_definition',
  BDO:  'behavior_definition',
};

function mapAbapType(abapType) {
  return ABAP_TYPE_MAP[abapType?.toUpperCase()] || null;
}

// ── Offline code analysis ─────────────────────────────────────────────────────

function analyzeSource(name, type, source) {
  const src = source || '';

  // ── Pattern detection ────────────────────────────────────────────────────────
  const patterns = [];

  if (/get_instance\s*\(/i.test(src))                             patterns.push('singleton');
  if (/_FACTORY/i.test(name) || /\bcreate_\w+\s*\(/i.test(src))  patterns.push('factory');
  if (/_MANAGER/i.test(name) || /cl_\w+_manager/i.test(name))    patterns.push('api-facade');
  if (/cl_bcs|lif_sender|cl_document_bcs/i.test(src))            patterns.push('notification-handler');
  if (/if_message_handler|check_\w*\(/i.test(src) && /_VALID/i.test(name)) patterns.push('validator');
  if (/status|state|workflow/i.test(name) && /set_status|change_status/i.test(src)) patterns.push('status-machine');
  if (/\bIF_\w+~\w+/i.test(src) && /\bimplementation\b/i.test(src))  patterns.push('rap-behavior');
  if (/\bON_\w+EVENT\b/i.test(src))                               patterns.push('rap-event-handler');
  if (/_CALC/i.test(name) || /calculate\s*\(/i.test(src))         patterns.push('automation-calculator');
  if (/\bIF_OO_ADT_CLASSRUN\b/i.test(src))                        patterns.push('runnable');

  // ── Key methods ──────────────────────────────────────────────────────────────
  const methodMatches = [...src.matchAll(/^\s*(?:class-)?methods?\s+(\w[\w\/]+)/gim)];
  const keyMethods = [...new Set(methodMatches.map(m => m[1]))]
    .filter(m => !/constructor|class_constructor/i.test(m))
    .slice(0, 10);

  // ── Dependencies ─────────────────────────────────────────────────────────────
  const depsSet = new Set();
  for (const m of src.matchAll(/interfaces\s+(\/?\w[\w\/]+)/gi))        depsSet.add(m[1].toUpperCase());
  for (const m of src.matchAll(/inheriting\s+from\s+(\/?\w[\w\/]+)/gi)) depsSet.add(m[1].toUpperCase());
  for (const m of src.matchAll(/create\s+object\s+\w+\s+type\s+(\/?\w[\w\/]+)/gi)) depsSet.add(m[1].toUpperCase());
  const dependencies = [...depsSet].filter(d => d !== name.toUpperCase()).slice(0, 20);

  // ── Tables accessed ──────────────────────────────────────────────────────────
  const tablesSet = new Set();
  for (const m of src.matchAll(/(?:from|into|update|modify|delete\s+from)\s+(\/?\w[\w\/]+)/gi)) {
    const t = m[1].toUpperCase();
    if (t.startsWith('/HEC1/') || t.startsWith('Z') || t.startsWith('Y')) tablesSet.add(t);
  }
  const tables = [...tablesSet].slice(0, 15);

  // ── Role description ─────────────────────────────────────────────────────────
  const docMatch = src.match(/"[!*][\s*]*([^\n]{10,100})/);
  const role = docMatch ? docMatch[1].trim() : inferRole(name, type, patterns);

  const summary = `${type} with ${(src.match(/\bmethod\b/gi) || []).length} methods, ` +
    `${tables.length} tables, patterns: [${patterns.join(', ') || 'none'}]`;

  return { role, patterns, keyMethods, dependencies, tables, summary };
}

function inferRole(name, type, patterns) {
  if (patterns.includes('singleton'))             return 'Singleton service manager';
  if (patterns.includes('factory'))               return 'Object factory / polymorphic dispatcher';
  if (patterns.includes('notification-handler'))  return 'Email / notification handler';
  if (patterns.includes('validator'))             return 'Business rule validator';
  if (patterns.includes('status-machine'))        return 'Status / workflow state machine';
  if (patterns.includes('rap-behavior'))          return 'RAP behavior implementation';
  if (patterns.includes('automation-calculator')) return 'Automation date/duration calculator';
  if (name.toUpperCase().includes('_CDD_'))  return 'CDD v1 component';
  if (name.toUpperCase().includes('_NCDD_')) return 'CDD 2.0 (NCDD) component';
  return type.charAt(0).toUpperCase() + type.slice(1).replace(/_/g, ' ');
}

// ── Package XML parser ────────────────────────────────────────────────────────

function parsePackageContents(xml) {
  const subPackages = [];
  const objects     = [];

  // SEU_ADT_REPOSITORY_OBJ_NODE blocks (ISD format)
  for (const block of xml.matchAll(/<SEU_ADT_REPOSITORY_OBJ_NODE>([\s\S]*?)<\/SEU_ADT_REPOSITORY_OBJ_NODE>/gi)) {
    const inner     = block[1];
    const typeMatch = inner.match(/<OBJECT_TYPE>([^<]+)<\/OBJECT_TYPE>/i);
    const nameMatch = inner.match(/<OBJECT_NAME>([^<]+)<\/OBJECT_NAME>/i);
    if (!typeMatch) continue;
    const rawType = typeMatch[1].trim();
    const name    = nameMatch ? nameMatch[1].trim() : null;
    if (rawType === 'DEVC/K') {
      if (name && !subPackages.includes(name)) subPackages.push(name);
    } else if (name) {
      const type = mapAbapType(rawType.split('/')[0]);
      if (type) objects.push({ name, type });
    }
  }

  // Fallback: adtcore attributes (other SAP systems)
  if (subPackages.length === 0 && objects.length === 0) {
    for (const m of xml.matchAll(/term="DEVC\/K"[^>]*label="([^"]+)"/gi)) subPackages.push(m[1]);
    for (const m of xml.matchAll(/<adtcore:packageRef[^>]*name="([^"]+)"/gi)) {
      if (!subPackages.includes(m[1])) subPackages.push(m[1]);
    }
    for (const m of xml.matchAll(/adtcore:name="([^"]+)"[^>]*adtcore:type="([^"]+)"/gi)) {
      const type = mapAbapType(m[2].split('/')[0]);
      if (type) objects.push({ name: m[1], type });
    }
  }

  return { subPackages, objects };
}

// ── Where-used XML parser ─────────────────────────────────────────────────────

function parseWhereUsed(xml) {
  const callers = [];
  // Direct name + CLAS type matches
  for (const m of xml.matchAll(/adtcore:name="([^"]+)"[^>]*adtcore:type="CLAS/gi)) {
    callers.push(m[1].toUpperCase());
  }
  // href fragments referencing class paths
  for (const m of xml.matchAll(/href="[^"]*\/classes\/([^"\/]+)/gi)) {
    const name = decodeURIComponent(m[1]).toUpperCase();
    if (!callers.includes(name)) callers.push(name);
  }
  return [...new Set(callers)];
}

// ── Tool name map ─────────────────────────────────────────────────────────────

const GET_TOOL = {
  class:               'GetClass',
  interface:           'GetInterface',
  program:             'GetProgram',
  function_group:      'GetFunctionGroup',
  cds_view:            'GetCDSView',
  behavior_definition: 'GetBehaviorDefinition',
};

const TOOL_INPUT_KEY = {
  GetClass:              'class_name',
  GetInterface:          'interface_name',
  GetProgram:            'program_name',
  GetFunctionGroup:      'group_name',
  GetCDSView:            'cds_view_name',
  GetBehaviorDefinition: 'bdef_name',
};

// ── Phase 1 — Package expansion ───────────────────────────────────────────────

async function phase1() {
  console.log('\n=== Phase 1: Package Expansion ===\n');

  const manifest = loadManifest();
  if (manifest.phase1Complete) {
    console.log('Phase 1 already complete. Use fix-manifest.js --unlock-phase1 to re-run.');
    return;
  }

  while (manifest.packageQueue.length > 0) {
    const pkg = manifest.packageQueue.shift();
    if ((manifest.visitedPackages || []).includes(pkg)) continue;

    console.log(`Expanding package: ${pkg}`);
    if (!manifest.visitedPackages) manifest.visitedPackages = [];
    manifest.visitedPackages.push(pkg);

    try {
      const result = await callTool('GetPackageContents', {
        package_name: pkg,
        system_id:    SYSTEM_ID,
        max_results:  5000,
      });

      if (result.isError) {
        console.warn(`  [WARN] ${pkg}: ${result.text}`);
        saveManifest(manifest);
        continue;
      }

      const xml = result.fileContent || result.text;
      const { subPackages, objects } = parsePackageContents(xml);

      for (const sp of subPackages) {
        if (!manifest.visitedPackages.includes(sp) && !manifest.packageQueue.includes(sp)) {
          manifest.packageQueue.push(sp);
        }
      }

      let added = 0;
      for (const obj of objects) {
        const key = `${obj.name}::${obj.type}`;
        const alreadyQueued = manifest.objectQueue.some(o => `${o.name}::${o.type}` === key);
        if (!alreadyQueued && !manifest.crawled[key]) {
          manifest.objectQueue.push({ name: obj.name, type: obj.type, package: pkg });
          added++;
        }
      }

      console.log(`  ${subPackages.length} sub-pkgs, ${added} new objects (total queue: ${manifest.objectQueue.length})`);
      saveManifest(manifest);

    } catch (e) {
      console.warn(`  [ERROR] ${pkg}: ${e.message}`);
      saveManifest(manifest);
    }
  }

  manifest.phase1Complete = true;
  saveManifest(manifest);
  console.log(`\nPhase 1 complete. ${manifest.objectQueue.length} objects queued.`);
}

// ── Phase 2 — Object crawl + analysis ────────────────────────────────────────

async function phase2() {
  console.log('\n=== Phase 2: Object Crawl + Analysis ===\n');

  const manifest = loadManifest();
  let queue = manifest.objectQueue.filter(o => !manifest.crawled[`${o.name}::${o.type}`]);
  if (limit > 0) queue = queue.slice(0, limit);

  console.log(`Objects to crawl: ${queue.length}`);
  let done = 0;

  async function processObject(obj) {
    const key      = `${obj.name}::${obj.type}`;
    const toolName = GET_TOOL[obj.type];

    if (!toolName) {
      console.warn(`  [SKIP] Unknown type: ${obj.type} for ${obj.name}`);
      await withManifestLock(() => {
        const m = loadManifest();
        m.crawled[key] = { crawledAt: new Date().toISOString(), status: 'skipped' };
        saveManifest(m);
      });
      return;
    }

    try {
      const result = await callTool(toolName, {
        [TOOL_INPUT_KEY[toolName]]: obj.name,
        system_id: SYSTEM_ID,
      });

      if (result.isError) {
        console.warn(`  [FAIL] ${obj.name}: ${String(result.text).slice(0, 120)}`);
        await withManifestLock(() => {
          const m = loadManifest();
          m.crawled[key] = { crawledAt: new Date().toISOString(), status: 'failed' };
          saveManifest(m);
        });
        return;
      }

      const source   = result.fileContent || '';
      const analysis = analyzeSource(obj.name, obj.type, source);

      const record = {
        name:      obj.name,
        type:      obj.type,
        package:   obj.package || '',
        source,
        lines:     source.split('\n').length,
        sizeBytes: Buffer.byteLength(source, 'utf8'),
        analysis,
        crawledAt: new Date().toISOString(),
      };

      const dir      = TYPE_DIR[obj.type];
      const filename = obj.name.replace(/\//g, '_').replace(/[^a-zA-Z0-9_\-]/g, '') + '.json';
      fs.writeFileSync(path.join(ABAP_DIR, dir, filename), JSON.stringify(record, null, 2));

      await withManifestLock(() => {
        const m = loadManifest();
        m.crawled[key] = { crawledAt: record.crawledAt, status: 'ok' };
        m.objectQueue  = m.objectQueue.filter(o => `${o.name}::${o.type}` !== key);
        saveManifest(m);
      });

      done++;
      if (done % 10 === 0 || done <= 5) {
        console.log(`  [${done}/${queue.length}] ${obj.name} (${obj.type}) — ${analysis.patterns.join(', ') || 'none'}`);
      }

    } catch (e) {
      console.warn(`  [ERROR] ${obj.name}: ${e.message}`);
      await withManifestLock(() => {
        const m = loadManifest();
        m.crawled[key] = { crawledAt: new Date().toISOString(), status: 'error', error: e.message };
        saveManifest(m);
      });
    }
  }

  for (let i = 0; i < queue.length; i += concurrency) {
    await Promise.all(queue.slice(i, i + concurrency).map(processObject));
    console.log(`Progress: ${Math.min(i + concurrency, queue.length)} / ${queue.length}`);
  }

  console.log(`\nPhase 2 complete. ${done} objects crawled.`);
}

// ── Phase 3 — Where-used ──────────────────────────────────────────────────────

async function phase3() {
  console.log('\n=== Phase 3: Where-Used ===\n');

  const targets = [];
  for (const [type, dir] of Object.entries(TYPE_DIR)) {
    const dirPath = path.join(ABAP_DIR, dir);
    if (!fs.existsSync(dirPath)) continue;
    for (const f of fs.readdirSync(dirPath)) {
      if (!f.endsWith('.json')) continue;
      try {
        const rec = JSON.parse(fs.readFileSync(path.join(dirPath, f), 'utf8'));
        const eligible =
          rec.type === 'interface' ||
          (rec.analysis?.patterns || []).some(p => ['api-facade','factory','singleton'].includes(p));
        if (eligible) targets.push({ rec, filePath: path.join(dirPath, f) });
      } catch {}
    }
  }

  console.log(`Where-used targets: ${targets.length}`);

  async function processWhereUsed({ rec, filePath }) {
    const whereUsedType = rec.type === 'interface' ? 'interface' : 'class';
    try {
      const result = await callTool('GetWhereUsed', {
        object_name: rec.name,
        object_type: whereUsedType,
        system_id:   SYSTEM_ID,
        max_results: 200,
      });

      if (result.isError) {
        console.warn(`  [FAIL] where-used ${rec.name}: ${String(result.text).slice(0, 80)}`);
        return;
      }

      const callers = parseWhereUsed(result.fileContent || result.text);
      if (callers.length > 0) {
        const updated = JSON.parse(fs.readFileSync(filePath, 'utf8'));
        updated.calledBy = [...new Set([...(updated.calledBy || []), ...callers])];
        fs.writeFileSync(filePath, JSON.stringify(updated, null, 2));
        console.log(`  ${rec.name}: ${callers.length} callers`);
      }
    } catch (e) {
      console.warn(`  [ERROR] where-used ${rec.name}: ${e.message}`);
    }
  }

  for (let i = 0; i < targets.length; i += concurrency) {
    await Promise.all(targets.slice(i, i + concurrency).map(processWhereUsed));
  }

  console.log('\nPhase 3 complete.');
}

// ── Main ──────────────────────────────────────────────────────────────────────

async function main() {
  console.log(`CDD Crawler — Phase ${phase} | System: ${SYSTEM_ID}`);

  const healthy = await checkServerHealth();
  if (!healthy) {
    console.error('\nERROR: sap-adt MCP server config not found in ~/.claude.json');
    process.exit(1);
  }

  acquireLock();

  if (phase === 1)      await phase1();
  else if (phase === 2) await phase2();
  else if (phase === 3) await phase3();

  mcpPool?.stop();
}

main().catch(e => {
  mcpPool?.stop();
  console.error('Fatal error:', e);
  process.exit(1);
});
