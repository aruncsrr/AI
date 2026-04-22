#!/usr/bin/env node
/**
 * SAP Knowledge Base — Weekly Sync Script
 *
 * Connects to SAP ISD system and refreshes knowledge base with latest changes.
 * Tracks object modification dates and only re-fetches changed objects.
 *
 * Run manually: node pipeline/weekly-sync.js
 * Cron weekly:  0 6 * * 1 (every Monday at 6am)
 *
 * Prerequisites: SAP MCP server must be running, .sap-systems.json configured
 */

import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dir = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dir, '..');
const SYNC_STATE_FILE = join(ROOT, 'knowledge/exports/sync-state.json');
const MCP_BASE = 'http://localhost:8080'; // SAP MCP server (via JCo bridge)

// Packages to sync (leaf sub-packages — parent packages contain only DEVC/K)
const PACKAGES = [
  // BOPF layer
  '/HEC1/BOPF_CLASS', '/HEC1/BOPF_CLEANUP', '/HEC1/BOPF_CONFIGURATION',
  '/HEC1/BOPF_COPY_CONTROL', '/HEC1/BOPF_CR_ACTION_HANDLER',
  '/HEC1/BOPF_FIELD_PROP_CONTROL', '/HEC1/BOPF_LOGGING',
  '/HEC1/BOPF_NODE_HANDLING', '/HEC1/BOPF_PRICE_AGGREGATION',
  '/HEC1/BOPF_PROCESS_NODE',
  '/HEC1/BOPF_DDIC',                           // DDIC objects for BOPF layer
  // CDD 1.0
  '/HEC1/CP_CDD_APP', '/HEC1/CP_CDD_IBP', '/HEC1/CP_CDD_REPORTING',
  '/HEC1/CP_CDD_UI',
  '/HEC1/CP_CDD_DDIC',                          // DDIC objects for CDD
  '/HEC1/CP_CDD_CONFIG_GDO_RL',                 // CDD config GDO release
  '/HEC1/CP_CDD_OBSOLETE',                      // Obsolete CDD objects
  '/HEC1/CP_CDD_ODATA',                         // OData services for CDD
  // NCDD 2.0
  '/HEC1/CP_NCDD_APP', '/HEC1/CP_NCDD_AUTO', '/HEC1/CP_NCDD_CDS',
  '/HEC1/CP_NCDD_DDIC', '/HEC1/NCDD_BUSINESS_SERVICES',
  '/HEC1/CP_NCDD_BUSINESS_SERVICE',             // NCDD business service layer
  '/HEC1/CP_NCDD_TEMP',                         // NCDD temporary/experimental
  '/HEC1/CP_NCDD_WORKLIST_BSP',                 // NCDD worklist BSP
  // Configurator — core
  '/HEC1/CONFIG_CLASS', '/HEC1/CONFIG_FACTORY', '/HEC1/CONFIG_TOOLS',
  '/HEC1/CONFIG_DDIC',                          // Configurator DDIC (largest: 2172 objects)
  '/HEC1/CONFIG_CONFIG',                        // Configurator config (WebDynpro-heavy)
  // Configurator — feeder layer (WebDynpro UI components)
  '/HEC1/CONFIG_FEEDER',
  '/HEC1/CONFIG_FEEDER_CHART',
  '/HEC1/CONFIG_FEEDER_DYNAMIC',
  '/HEC1/CONFIG_FEEDER_DYN_LIST',               // Sub-package of DYNAMIC
  '/HEC1/CONFIG_FEEDER_FORM',
  '/HEC1/CONFIG_FEEDER_LIST',
  '/HEC1/CONFIG_FEEDER_MASTER',
  '/HEC1/CONFIG_FEEDER_REPEATER',
  '/HEC1/CONFIG_FEEDER_SEARCH',
  '/HEC1/CONFIG_FEEDER_TREE',
  // Provisioning V2 (flat package — objects directly inside)
  '/HEC1/CP_PROVISIONING_V2',
];

// Object types to track (all fetchable ABAP types — see type→tool mapping below)
const OBJECT_TYPES = ['CLAS', 'INTF', 'PROG', 'FUGR', 'DTEL', 'TABL', 'TTYP', 'DOMA',
                      'DDLS', 'DDLX', 'BDEF', 'SRVD', 'SRVB', 'PROG/I'];

// ─── State management ──────────────────────────────────────────────────────

function loadSyncState() {
  if (!existsSync(SYNC_STATE_FILE)) {
    return { lastSync: null, objects: {}, packages: {} };
  }
  return JSON.parse(readFileSync(SYNC_STATE_FILE, 'utf8'));
}

function saveSyncState(state) {
  state.lastSync = new Date().toISOString();
  writeFileSync(SYNC_STATE_FILE, JSON.stringify(state, null, 2));
}

// ─── MCP API calls ──────────────────────────────────────────────────────────

async function mcpCall(tool, params) {
  const resp = await fetch(`${MCP_BASE}/api/tools/${tool}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params)
  });
  if (!resp.ok) throw new Error(`MCP ${tool} returned ${resp.status}: ${await resp.text()}`);
  return resp.json();
}

// ─── Sync functions ────────────────────────────────────────────────────────

async function getPackageObjects(packageName) {
  console.log(`  Fetching package contents: ${packageName}`);
  const result = await mcpCall('GetPackageContents', {
    package_name: packageName,
    max_results: 2000
  });
  return result.objects || [];
}

async function getObjectLastChanged(objectName, objectType) {
  const typeMap = {
    'CLAS': 'class', 'INTF': 'interface', 'PROG': 'program',
    'FUGR': 'function_group', 'PROG/I': 'include',
    'DDLS': 'cds_view', 'BDEF': 'behavior_definition', 'SRVD': 'service_definition'
  };
  const type = typeMap[objectType];
  if (!type) return null;

  const history = await mcpCall('GetVersionHistory', {
    object_name: objectName,
    object_type: type
  });
  const versions = history.versions || [];
  if (versions.length === 0) return null;

  // Most recent version
  return {
    date: versions[0].date,
    author: versions[0].author,
    transport: versions[0].transport
  };
}

async function fetchObjectSource(objectName, objectType) {
  const toolMap = {
    'CLAS': ['GetClass', { class_name: objectName }],
    'INTF': ['GetInterface', { interface_name: objectName }],
    'PROG': ['GetProgram', { program_name: objectName }],
    'FUGR': ['GetFunctionGroup', { group_name: objectName }],
    'PROG/I': ['GetInclude', { include_name: objectName }],
    'DTEL': ['GetDataElement', { data_element_name: objectName }],
    'DOMA': ['GetDomain', { domain_name: objectName }],
    'DDLS': ['GetCDSView', { cds_view_name: objectName }],
    'BDEF': ['GetBehaviorDefinition', { bdef_name: objectName }],
    'SRVD': ['GetServiceDefinition', { srvd_name: objectName }],
    'SRVB': ['GetServiceBinding', { srvb_name: objectName }],
  };
  const entry = toolMap[objectType];
  if (!entry) return null;

  return mcpCall(entry[0], entry[1]);
}

// ─── Knowledge file writer ─────────────────────────────────────────────────

function writeObjectToKnowledge(objectName, objectType, source, lastChanged) {
  const typeDir = {
    CLAS: 'classes', INTF: 'interfaces', PROG: 'programs', FUGR: 'function_groups',
    'PROG/I': 'includes', DTEL: 'data_elements', DOMA: 'domains',
    DDLS: 'cds_views', DDLX: 'cds_extensions', TABL: 'tables', TTYP: 'table_types',
    BDEF: 'behavior_definitions', SRVD: 'service_definitions', SRVB: 'service_bindings'
  };
  const dir = join(ROOT, 'knowledge/abap', typeDir[objectType] || objectType.toLowerCase());
  mkdirSync(dir, { recursive: true });

  const ext = ['DTEL', 'DOMA', 'TABL', 'TTYP', 'SRVB'].includes(objectType) ? '.xml' : '.abap';
  const filename = objectName.replace(/\//g, '_').replace(/[^a-zA-Z0-9_-]/g, '') + ext;
  const filepath = join(dir, filename);

  const content = `" Object: ${objectName}
" Type: ${objectType}
" Last changed: ${lastChanged?.date || 'unknown'} by ${lastChanged?.author || 'unknown'}
" Transport: ${lastChanged?.transport || 'N/A'}
" Synced: ${new Date().toISOString()}

${source.source || source.content || '-- Source not available --'}`;

  writeFileSync(filepath, content);
  return filepath;
}

// ─── Main sync ─────────────────────────────────────────────────────────────

async function runSync() {
  console.log('='.repeat(60));
  console.log('SAP Knowledge Base — Weekly Sync');
  console.log(`Started: ${new Date().toISOString()}`);
  console.log('='.repeat(60));

  const state = loadSyncState();
  const stats = { checked: 0, updated: 0, skipped: 0, errors: 0 };

  // Check MCP server availability
  try {
    await fetch(`${MCP_BASE}/health`);
    console.log('✓ SAP MCP server reachable');
  } catch {
    console.error('✗ SAP MCP server not reachable at', MCP_BASE);
    console.error('  Start with: java -jar jco-service/target/jco-service-1.0.0.jar --mcp');
    process.exit(1);
  }

  for (const pkg of PACKAGES) {
    console.log(`\nSyncing package: ${pkg}`);
    try {
      const objects = await getPackageObjects(pkg);
      console.log(`  Found ${objects.length} objects`);

      for (const obj of objects) {
        if (!OBJECT_TYPES.includes(obj.type)) continue;
        stats.checked++;

        try {
          const lastChanged = await getObjectLastChanged(obj.name, obj.type);
          const stateKey = `${obj.type}:${obj.name}`;
          const knownDate = state.objects[stateKey];

          // Skip if not changed since last sync
          if (knownDate && lastChanged?.date && knownDate >= lastChanged.date) {
            stats.skipped++;
            continue;
          }

          console.log(`  Updating: ${obj.type} ${obj.name} (${lastChanged?.date || 'unknown'})`);
          const source = await fetchObjectSource(obj.name, obj.type);
          if (source) {
            const path = writeObjectToKnowledge(obj.name, obj.type, source, lastChanged);
            console.log(`    → Written to ${path}`);
            state.objects[stateKey] = lastChanged?.date || new Date().toISOString();
            stats.updated++;
          }
        } catch (e) {
          console.warn(`  Warning: Could not sync ${obj.name}: ${e.message}`);
          stats.errors++;
        }
      }
    } catch (e) {
      console.error(`Error syncing package ${pkg}: ${e.message}`);
      stats.errors++;
    }
  }

  // Save updated state
  saveSyncState(state);

  console.log('\n' + '='.repeat(60));
  console.log('Sync Summary:');
  console.log(`  Checked:  ${stats.checked}`);
  console.log(`  Updated:  ${stats.updated}`);
  console.log(`  Skipped:  ${stats.skipped} (no changes)`);
  console.log(`  Errors:   ${stats.errors}`);
  console.log('='.repeat(60));

  if (stats.updated > 0) {
    console.log('\nRebuilding search index...');
    const { execSync } = await import('child_process');
    execSync('node pipeline/build-search-index.js', { stdio: 'inherit', cwd: ROOT });
    console.log('✓ Search index rebuilt');

    // Wire in graph-builder if available
    try {
      execSync('node pipeline/graph-builder.js', { stdio: 'inherit', cwd: ROOT });
      console.log('✓ Dependency graph rebuilt');
    } catch {
      console.warn('  graph-builder.js not found or failed — skipping graph rebuild');
    }
  } else {
    console.log('\nNo changes — search index unchanged');
  }

  // Fetch recent ST22 runtime errors (governance: read-only observe layer)
  console.log('\nFetching recent ST22 runtime errors...');
  try {
    const dumps = await mcpCall('ListRuntimeErrors', { max_results: 50 });
    const dumpDir = join(ROOT, 'knowledge/runtime_errors');
    mkdirSync(dumpDir, { recursive: true });
    const filename = `st22_${new Date().toISOString().slice(0, 10)}.json`;
    writeFileSync(join(dumpDir, filename), JSON.stringify(dumps, null, 2));
    console.log(`✓ ST22 dumps saved to knowledge/runtime_errors/${filename}`);
  } catch (e) {
    console.warn(`  Warning: Could not fetch ST22 runtime errors: ${e.message}`);
  }
}

runSync().catch(e => {
  console.error('Fatal sync error:', e);
  process.exit(1);
});
