/**
 * SAP Knowledge Base - Offline/Online Search Server
 *
 * Offline mode: BM25 search over pre-built JSON index (no SAP connection needed)
 * Online mode:  Live queries against SAP ISD via ADT REST MCP tools
 *
 * Start: node app/server.js [--port 3030]
 */

import { createServer } from 'http';
import { readFileSync, existsSync, readdirSync, statSync } from 'fs';
import { join, dirname, relative, extname, basename } from 'path';
import { fileURLToPath } from 'url';

const __dir = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dir, '..');
const PORT = parseInt(process.argv.find(a => a.startsWith('--port='))?.split('=')[1] || '3030');

// ─── Load pre-built search index ───────────────────────────────────────────

const INDEX_PATH = join(ROOT, 'knowledge/exports/search-index.json');
let searchIndex = null;

if (existsSync(INDEX_PATH)) {
  console.log('Loading search index...');
  searchIndex = JSON.parse(readFileSync(INDEX_PATH, 'utf8'));
  console.log(`Index loaded: ${searchIndex.meta.docCount} docs, ${searchIndex.meta.termCount} terms`);
} else {
  console.warn('WARNING: Search index not found. Run: node pipeline/build-search-index.js');
}

// ─── BM25 search (server-side) ─────────────────────────────────────────────

const STOP_WORDS = new Set([
  'a','an','the','and','or','but','in','on','at','to','for','of','with',
  'by','from','is','it','its','be','was','are','were','has','have','had',
  'that','this','these','those','as','do','not','all','can','will','would',
  'also','than','then','when','where','which','who','how','what','if','into',
  'each','so','use','used','using','more','one','two','may','per','any'
]);

function tokenize(text) {
  return text.toLowerCase()
    .replace(/[^a-z0-9/_-]/g, ' ')
    .split(/\s+/)
    .filter(t => t.length >= 2 && !STOP_WORDS.has(t));
}

function bm25Search(query, topK = 15) {
  if (!searchIndex) return { results: [], error: 'Index not loaded' };

  const tokens = tokenize(query);
  const queryTerms = [...tokens];
  for (let i = 0; i < tokens.length - 1; i++) {
    queryTerms.push(tokens[i] + ' ' + tokens[i+1]);
  }

  const scores = {};
  for (const term of queryTerms) {
    const postings = searchIndex.index[term] || [];
    for (const { id, score } of postings) {
      scores[id] = (scores[id] || 0) + score;
    }
  }

  const results = Object.entries(scores)
    .sort((a, b) => b[1] - a[1])
    .slice(0, topK)
    .map(([id, score]) => ({
      ...searchIndex.docs[parseInt(id)],
      score: Math.round(score * 100) / 100
    }));

  return { results, query, termCount: queryTerms.length };
}

// ─── SAP ADT live search (online mode) ────────────────────────────────────

async function adtSearch(query) {
  // This calls the local MCP server which proxies to SAP ADT
  // The MCP server must be running (java -jar jco-service/target/jco-service-1.0.0.jar --mcp)
  try {
    const resp = await fetch('http://localhost:8080/api/search', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query, maxResults: 20 })
    });
    if (!resp.ok) throw new Error(`MCP API returned ${resp.status}`);
    return await resp.json();
  } catch (e) {
    return { error: `SAP ADT connection failed: ${e.message}`, query };
  }
}

// ─── Knowledge directory walker ────────────────────────────────────────────

function walkKnowledgeDir(dir) {
  const results = [];
  try {
    for (const entry of readdirSync(dir)) {
      const full = join(dir, entry);
      const st = statSync(full);
      const rel = relative(ROOT, full);
      if (st.isDirectory()) {
        results.push({ type: 'dir', path: rel, name: entry, children: walkKnowledgeDir(full) });
      } else if (['.md', '.abap', '.ddl'].includes(extname(entry))) {
        results.push({ type: 'file', path: rel, name: basename(entry, extname(entry)),
          ext: extname(entry), size: st.size, modified: st.mtime });
      }
    }
  } catch {}
  return results;
}

// ─── Feature analysis helper ───────────────────────────────────────────────

function buildFeatureAnalysis(requirement, sources) {
  const req = requirement.toLowerCase();
  const suggestions = [];

  if (sources.length === 0) {
    return 'No relevant knowledge found. Try a more specific query.';
  }

  const topSource = sources[0];
  suggestions.push(`## Feature Analysis: "${requirement}"\n`);
  suggestions.push(`### Most Relevant Knowledge: ${topSource.title}`);
  suggestions.push(`Path: \`${topSource.filePath}\`\n`);

  if (req.includes('reject') || req.includes('reason') || req.includes('categor')) {
    suggestions.push('### Implementation Guidance\n');
    suggestions.push('See: `knowledge/patterns/feature-analysis-rejection-categories.md`\n');
    suggestions.push('**Key tables:** Domain `HEC_REJ_REASON` (or check table) → `HEC_CDD_REJ_REASON_VALUE` field\n');
    suggestions.push('**Key classes:** `/HEC1/CL_CDD_MANAGER~REJECT_REQUEST`, FPM feeder or RAP BDEF action\n');
  }

  if (req.includes('status') || req.includes('transition') || req.includes('workflow')) {
    suggestions.push('**Status framework:** `/HEC1/CL_STATUS_HANDLER` singleton — status transitions are customizable via config table, not hard-coded.\n');
  }

  if (req.includes('bopf') || req.includes('node') || req.includes('field')) {
    suggestions.push('**BOPF pattern:** Add fields to DB table, extend include structure, update `CL_CDD_BOPF_UPDATE~UPDATE_BOPF_NODES`. Entry in `/HEC1/CDDBOPFCNF` may be needed.\n');
  }

  if (req.includes('report') || req.includes('cds') || req.includes('view')) {
    suggestions.push('**Reporting pattern:** CDS views in `/HEC1/C_CDD_REQUEST` are the main consumption layer. New fields need to be surfaced there.\n');
  }

  suggestions.push('\n### Top Matching Knowledge Files\n');
  sources.slice(0, 5).forEach((s, i) => {
    suggestions.push(`${i+1}. **${s.title}** (score: ${s.score}) — \`${s.filePath}\``);
  });

  return suggestions.join('\n');
}

// ─── Request handlers ──────────────────────────────────────────────────────

function serveFile(res, filePath, contentType) {
  try {
    const content = readFileSync(filePath);
    res.writeHead(200, { 'Content-Type': contentType });
    res.end(content);
  } catch {
    res.writeHead(404);
    res.end('Not found');
  }
}

async function handleRequest(req, res) {
  const url = new URL(req.url, `http://localhost:${PORT}`);

  // CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    return res.end();
  }

  // Static files
  if (url.pathname === '/' || url.pathname === '/index.html') {
    return serveFile(res, join(__dir, 'public/index.html'), 'text/html; charset=utf-8');
  }

  // Serve the search index as static asset for client-side use
  if (url.pathname === '/search-index.json') {
    return serveFile(res, INDEX_PATH, 'application/json');
  }

  // Serve individual knowledge files for reading
  if (url.pathname.startsWith('/knowledge/')) {
    const relPath = url.pathname.slice(1); // strip leading /
    return serveFile(res, join(ROOT, relPath), 'text/plain; charset=utf-8');
  }

  // API: offline BM25 search
  if (url.pathname === '/api/search/offline' && req.method === 'POST') {
    let body = '';
    req.on('data', d => body += d);
    req.on('end', () => {
      try {
        const { query, topK } = JSON.parse(body);
        const result = bm25Search(query, topK || 15);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(result));
      } catch (e) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: e.message }));
      }
    });
    return;
  }

  // API: online SAP ADT search
  if (url.pathname === '/api/search/online' && req.method === 'POST') {
    let body = '';
    req.on('data', d => body += d);
    req.on('end', async () => {
      try {
        const { query } = JSON.parse(body);
        const result = await adtSearch(query);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(result));
      } catch (e) {
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: e.message }));
      }
    });
    return;
  }

  // API: index metadata
  if (url.pathname === '/api/index/meta') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(searchIndex?.meta || { error: 'Index not loaded' }));
    return;
  }

  // API: get full content of a knowledge file
  if (url.pathname === '/api/content' && req.method === 'GET') {
    const file = url.searchParams.get('file');
    if (!file || file.includes('..')) {
      res.writeHead(400);
      return res.end('Invalid file path');
    }
    const fullPath = join(ROOT, file);
    try {
      const content = readFileSync(fullPath, 'utf8');
      res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end(content);
    } catch {
      res.writeHead(404);
      res.end('File not found');
    }
    return;
  }

  // API: list all knowledge files (for hierarchy view)
  if (url.pathname === '/api/knowledge/list') {
    const files = walkKnowledgeDir(join(ROOT, 'knowledge'));
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ files }));
    return;
  }

  // API: feature analysis — given a requirement, find relevant files
  if (url.pathname === '/api/analyze' && req.method === 'POST') {
    let body = '';
    req.on('data', d => body += d);
    req.on('end', () => {
      try {
        const { requirement } = JSON.parse(body);
        const results = bm25Search(requirement, 8);
        const analysis = buildFeatureAnalysis(requirement, results.results || []);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ analysis, sources: results.results || [] }));
      } catch (e) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: e.message }));
      }
    });
    return;
  }

  // Serve hierarchy page
  if (url.pathname === '/hierarchy') {
    return serveFile(res, join(__dir, 'public/hierarchy.html'), 'text/html; charset=utf-8');
  }

  res.writeHead(404);
  res.end('Not found');
}

// ─── Start server ──────────────────────────────────────────────────────────

const server = createServer(handleRequest);
server.listen(PORT, () => {
  console.log(`\nSAP Knowledge Base running at http://localhost:${PORT}`);
  console.log(`Offline search: ${searchIndex ? '✓ Ready' : '✗ Index missing — run build-search-index.js'}`);
  console.log(`Online mode: Requires SAP MCP server at localhost:8080\n`);
});
