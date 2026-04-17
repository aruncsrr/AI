#!/usr/bin/env node
/**
 * Manifest repair utility.
 * Scans knowledge/abap/ on disk, rebuilds the crawled map from actual files,
 * removes those from objectQueue, and optionally sets phase1Complete.
 *
 * Usage:
 *   node fix-manifest.js                  -- sync disk to manifest
 *   node fix-manifest.js --lock-phase1    -- also set phase1Complete=true
 *   node fix-manifest.js --unlock-phase1  -- clear phase1Complete (allow re-run)
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const config        = JSON.parse(fs.readFileSync(path.join(__dirname, 'pipeline-config.json'), 'utf8'));
const KNOWLEDGE_DIR = path.resolve(__dirname, config.knowledgeDir);
const CRAWL_DIR     = path.join(KNOWLEDGE_DIR, 'crawl');
const ABAP_DIR      = path.join(KNOWLEDGE_DIR, 'abap');
const MANIFEST_FILE = path.join(CRAWL_DIR, 'manifest.json');

const TYPE_DIR = {
  class: 'classes',
  interface: 'interfaces',
  program: 'programs',
  function_group: 'function-groups',
  cds_view: 'cds-views',
  behavior_definition: 'behavior-definitions',
};

function loadManifest() {
  if (!fs.existsSync(MANIFEST_FILE)) return null;
  return JSON.parse(fs.readFileSync(MANIFEST_FILE, 'utf8'));
}

function saveManifest(m) {
  fs.writeFileSync(MANIFEST_FILE, JSON.stringify(m, null, 2));
}

// Scan all JSON files on disk and return a map: "name::type" → true
function scanDisk() {
  const found = {};
  for (const [type, dir] of Object.entries(TYPE_DIR)) {
    const dirPath = path.join(ABAP_DIR, dir);
    if (!fs.existsSync(dirPath)) continue;
    for (const f of fs.readdirSync(dirPath)) {
      if (!f.endsWith('.json')) continue;
      try {
        const rec = JSON.parse(fs.readFileSync(path.join(dirPath, f), 'utf8'));
        if (rec.name && rec.type) {
          found[`${rec.name}::${rec.type}`] = { crawledAt: rec.crawledAt || new Date().toISOString(), status: 'ok' };
        }
      } catch (e) { /* skip malformed */ }
    }
  }
  return found;
}

const args = process.argv.slice(2);
const lockPhase1   = args.includes('--lock-phase1');
const unlockPhase1 = args.includes('--unlock-phase1');

const manifest = loadManifest();
if (!manifest) {
  console.error('No manifest.json found. Run phase 1 first.');
  process.exit(1);
}

// Rebuild crawled map from disk
const diskCrawled = scanDisk();
const before = Object.keys(manifest.crawled).length;
manifest.crawled = { ...diskCrawled };
const after = Object.keys(manifest.crawled).length;

// Remove crawled objects from objectQueue
const queueBefore = manifest.objectQueue.length;
manifest.objectQueue = manifest.objectQueue.filter(obj => {
  const key = `${obj.name}::${obj.type}`;
  return !manifest.crawled[key];
});
const queueAfter = manifest.objectQueue.length;

if (lockPhase1)   manifest.phase1Complete = true;
if (unlockPhase1) manifest.phase1Complete = false;

saveManifest(manifest);

console.log(`Manifest sync complete:`);
console.log(`  crawled map: ${before} → ${after} entries (from disk)`);
console.log(`  objectQueue: ${queueBefore} → ${queueAfter} remaining`);
console.log(`  phase1Complete: ${manifest.phase1Complete}`);
if (lockPhase1)   console.log('  → phase1Complete set to TRUE (phase 1 locked)');
if (unlockPhase1) console.log('  → phase1Complete set to FALSE (phase 1 unlocked)');
