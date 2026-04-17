#!/usr/bin/env node
/**
 * Sync pipeline output to the Java MCP server cache.
 *
 * After phase 2, the pipeline has JSON records in knowledge/abap/{type}/*.json.
 * This script copies the source code into the Java cache directory so that
 * cdd_local_search and cdd_local_get_code serve all crawled objects offline.
 *
 * Also optionally calls POST /api/pipeline/reload-cache to hot-reload the index.
 *
 * Usage:
 *   node sync-java-cache.js                  -- sync and reload
 *   node sync-java-cache.js --no-reload      -- sync only (no HTTP call)
 *   node sync-java-cache.js --dry-run        -- show what would be written
 */

import fs from 'fs';
import os from 'os';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const config        = JSON.parse(fs.readFileSync(path.join(__dirname, 'pipeline-config.json'), 'utf8'));
const JAVA_URL      = config.javaServerUrl || 'http://localhost:8080';
const KNOWLEDGE_DIR = path.resolve(__dirname, config.knowledgeDir);
const ABAP_DIR      = path.join(KNOWLEDGE_DIR, 'abap');

// Resolve the Java cache directory (support ~ expansion)
function resolveCacheDir() {
  const raw = config.javaCacheDir || '~/sap-ai-assistant/cdd-cache';
  return raw.startsWith('~') ? path.join(os.homedir(), raw.slice(1)) : raw;
}

const CACHE_DIR  = resolveCacheDir();
const INDEX_FILE = path.join(CACHE_DIR, 'index.json');

const args      = process.argv.slice(2);
const dryRun    = args.includes('--dry-run');
const noReload  = args.includes('--no-reload');

// ── Extension map ─────────────────────────────────────────────────────────────

const EXT = {
  class:               '.abap',
  interface:           '.abap',
  program:             '.abap',
  function_group:      '.abap',
  cds_view:            '.ddl',
  behavior_definition: '.abap',
};

const TYPE_DIR = {
  class:               'classes',
  interface:           'interfaces',
  program:             'programs',
  function_group:      'function-groups',
  cds_view:            'cds-views',
  behavior_definition: 'behavior-definitions',
};

function sanitizeName(name) {
  return name.replace(/\//g, '_').replace(/[^a-zA-Z0-9_\-]/g, '');
}

// ── Load existing index ───────────────────────────────────────────────────────

function loadIndex() {
  if (fs.existsSync(INDEX_FILE)) {
    try { return JSON.parse(fs.readFileSync(INDEX_FILE, 'utf8')); } catch {}
  }
  return {};
}

function saveIndex(idx) {
  if (!dryRun) {
    fs.mkdirSync(path.dirname(INDEX_FILE), { recursive: true });
    fs.writeFileSync(INDEX_FILE, JSON.stringify(idx, null, 2));
  }
}

// ── Main sync ─────────────────────────────────────────────────────────────────

async function main() {
  console.log(`Syncing pipeline output → Java cache at ${CACHE_DIR}`);
  if (dryRun) console.log('(DRY RUN — no files written)');

  const index = loadIndex();
  let added = 0, updated = 0, skipped = 0;

  for (const [type, dir] of Object.entries(TYPE_DIR)) {
    const srcDir = path.join(ABAP_DIR, dir);
    if (!fs.existsSync(srcDir)) continue;

    for (const f of fs.readdirSync(srcDir)) {
      if (!f.endsWith('.json')) continue;

      let rec;
      try {
        rec = JSON.parse(fs.readFileSync(path.join(srcDir, f), 'utf8'));
      } catch { continue; }

      if (!rec.source || !rec.name || !rec.type) { skipped++; continue; }

      const key     = rec.name.toUpperCase();
      const relPath = `${rec.type}/${sanitizeName(rec.name)}${EXT[rec.type] || '.abap'}`;
      const destPath = path.join(CACHE_DIR, relPath);

      const isNew = !index[key];

      if (!dryRun) {
        fs.mkdirSync(path.dirname(destPath), { recursive: true });
        fs.writeFileSync(destPath, rec.source, 'utf8');
      }

      // Update index entry (matches Java CacheEntry structure)
      index[key] = {
        name:        rec.name,
        type:        rec.type,
        pkg:         rec.package || '',
        description: rec.analysis?.role || rec.description || '',
        path:        relPath,
        cachedAt:    rec.crawledAt || new Date().toISOString(),
        sizeBytes:   Buffer.byteLength(rec.source, 'utf8'),
        failed:      false,
      };

      if (isNew) added++; else updated++;
    }
  }

  saveIndex(index);

  console.log(`Sync complete: ${added} added, ${updated} updated, ${skipped} skipped.`);
  console.log(`Index entries: ${Object.keys(index).length}`);

  if (!noReload && !dryRun) {
    console.log(`\nReloading Java cache index at ${JAVA_URL}...`);
    try {
      const res = await fetch(`${JAVA_URL}/api/pipeline/reload-cache`, { method: 'POST' });
      if (res.ok) {
        const data = await res.json();
        console.log(`Reload OK: ${data.message}`);
      } else {
        console.warn(`Reload HTTP ${res.status} — cache will reload on next Java server restart.`);
      }
    } catch (e) {
      console.warn(`Reload skipped (server not running): ${e.message}`);
      console.warn('The cache files are in place — they will load on next Java server start.');
    }
  }
}

main().catch(e => {
  console.error('Fatal:', e);
  process.exit(1);
});
