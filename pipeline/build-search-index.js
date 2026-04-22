#!/usr/bin/env node
/**
 * SAP Knowledge Base Search Index Builder
 *
 * Reads all markdown files from knowledge/ directory and builds a BM25-optimized
 * search index as static JSON. No internet connection or SAP system required.
 *
 * Output: knowledge/exports/search-index.json
 *
 * Usage: node pipeline/build-search-index.js
 */

import { readFileSync, writeFileSync, readdirSync, statSync } from 'fs';
import { join, relative, extname, basename } from 'path';

const ROOT = '/Users/I766689/Desktop/sap-ai-assistant';
const KNOWLEDGE_DIR = join(ROOT, 'knowledge');
const OUTPUT_FILE = join(ROOT, 'knowledge/exports/search-index.json');

// BM25 parameters
const BM25_K1 = 1.5;  // term frequency saturation
const BM25_B  = 0.75; // length normalization factor

// ─── File walker ────────────────────────────────────────────────────────────

function walkDir(dir, exts = ['.md', '.ddl', '.abap']) {
  const results = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const st = statSync(full);
    if (st.isDirectory()) {
      results.push(...walkDir(full, exts));
    } else if (exts.includes(extname(entry).toLowerCase())) {
      results.push(full);
    }
  }
  return results;
}

// ─── Text preprocessing ──────────────────────────────────────────────────────

const STOP_WORDS = new Set([
  'a','an','the','and','or','but','in','on','at','to','for','of','with',
  'by','from','is','it','its','be','was','are','were','has','have','had',
  'that','this','these','those','as','do','not','all','can','will','would',
  'also','than','then','when','where','which','who','how','what','if','into',
  'each','so','use','used','using','more','one','two','may','per','any',
  'only','both','other','such','same','well','he','she','they','we','you',
  'i','me','him','her','us','them','my','your','his','our','their','its'
]);

function tokenize(text) {
  return text
    .toLowerCase()
    .replace(/```[\s\S]*?```/g, ' ')   // strip code blocks (preserve structure)
    .replace(/[^a-z0-9/_-]/g, ' ')
    .split(/\s+/)
    .filter(t => t.length >= 2 && !STOP_WORDS.has(t));
}

// Extract n-grams for multi-word SAP terms (e.g. "bopf node handler")
function bigrams(tokens) {
  const bg = [];
  for (let i = 0; i < tokens.length - 1; i++) {
    bg.push(`${tokens[i]} ${tokens[i+1]}`);
  }
  return bg;
}

// ─── Document parser ─────────────────────────────────────────────────────────

function parseDocument(filePath) {
  const raw = readFileSync(filePath, 'utf8');
  const rel = relative(ROOT, filePath);

  // Extract title from first H1/H2
  const titleMatch = raw.match(/^#+ (.+)$/m);
  const title = titleMatch ? titleMatch[1].trim() : basename(filePath, extname(filePath));

  // Extract section structure (H2/H3 headings with their content)
  const sections = [];
  const sectionRegex = /^(#{2,3})\s+(.+)$/gm;
  let match;
  let lastIdx = 0;
  let lastHeading = title;
  let headings = [];

  // Find all heading positions
  while ((match = sectionRegex.exec(raw)) !== null) {
    headings.push({ idx: match.index, heading: match[2], level: match[1].length });
  }

  for (let i = 0; i < headings.length; i++) {
    const start = headings[i].idx;
    const end   = i + 1 < headings.length ? headings[i+1].idx : raw.length;
    const content = raw.slice(start, end);
    sections.push({ heading: headings[i].heading, content, level: headings[i].level });
  }

  // Extract SAP identifiers (special boost)
  const sapIdents = [...new Set(
    (raw.match(/\/[A-Z0-9_]+\/[A-Z0-9_]+/g) || [])   // namespaced /HEC1/CL_*
    .concat(raw.match(/[A-Z][A-Z0-9_]{2,40}/g) || []) // ABAP identifiers
    .filter(id => id.length >= 4)
  )];

  // Category from path
  const parts = rel.split('/');
  const category = parts.length >= 2 ? parts[1] : 'general';

  // Source type
  const ext = extname(filePath);
  const sourceType = ext === '.md' ? 'knowledge' : ext === '.abap' ? 'abap' : 'ddl';

  // Full text for indexing
  const fullText = raw;

  return {
    id: rel,
    title,
    category,
    sourceType,
    filePath: rel,
    sapIdentifiers: sapIdents.slice(0, 50),
    sections: sections.map(s => ({
      heading: s.heading,
      snippet: s.content.slice(0, 500).replace(/\n+/g, ' ').trim()
    })),
    fullText
  };
}

// ─── TF-IDF / BM25 index builder ─────────────────────────────────────────────

function buildIndex(documents) {
  const N = documents.length;

  // Term → document frequency
  const df = Object.create(null);
  // doc → term → raw tf
  const docTerms = [];

  for (const doc of documents) {
    const tokens = [
      ...tokenize(doc.fullText),
      ...bigrams(tokenize(doc.fullText)),
      // Boost SAP identifiers with 3x weight
      ...doc.sapIdentifiers.flatMap(id => tokenize(id).flatMap(t => [t, t, t]))
    ];

    const tf = Object.create(null);
    for (const t of tokens) {
      tf[t] = (tf[t] || 0) + 1;
    }
    docTerms.push(tf);

    for (const t of Object.keys(tf)) {
      df[t] = (df[t] || 0) + 1;
    }
  }

  // Average document length
  const avgDL = docTerms.reduce((sum, tf) => {
    return sum + Object.values(tf).reduce((a, b) => a + b, 0);
  }, 0) / N;

  // Build inverted index: term → [{docId, score}]
  const index = Object.create(null);

  for (let i = 0; i < documents.length; i++) {
    const tf = docTerms[i];
    const docLen = Object.values(tf).reduce((a, b) => a + b, 0);

    for (const [term, rawTF] of Object.entries(tf)) {
      const idf = Math.log((N - df[term] + 0.5) / (df[term] + 0.5) + 1);
      const norm = (rawTF * (BM25_K1 + 1)) / (rawTF + BM25_K1 * (1 - BM25_B + BM25_B * docLen / avgDL));
      const score = idf * norm;

      if (score > 0.1) {
        if (!index[term]) index[term] = [];
        index[term].push({ id: i, score: Math.round(score * 1000) / 1000 });
      }
    }
  }

  // Sort each posting list by score descending
  for (const term of Object.keys(index)) {
    index[term].sort((a, b) => b.score - a.score);
    // Keep top 100 per term to limit index size
    if (index[term].length > 100) index[term] = index[term].slice(0, 100);
  }

  return { index, avgDL, N };
}

// ─── Search function (embedded in index for client-side use) ─────────────────

function buildSearchFunction() {
  return `
// BM25 search — call search(query, index, topK)
function search(query, searchData, topK = 10) {
  const { index, docs } = searchData;

  // Simple tokenizer matching the builder
  const stopWords = new Set(['a','an','the','and','or','but','in','on','at','to','for','of','with','by','from','is','it','its','be','was','are','were','has','have','had','that','this','these','those','as','do','not','all','can','will','would','also','than','then','when','where','which','who','how','what','if','into','each','so','use','used','using','more','one','two','may','per','any','only','both','other','such','same','well']);

  const tokens = query.toLowerCase()
    .replace(/[^a-z0-9/_-]/g, ' ')
    .split(/\\s+/)
    .filter(t => t.length >= 2 && !stopWords.has(t));

  // Add bigrams
  const queryTerms = [...tokens];
  for (let i = 0; i < tokens.length - 1; i++) {
    queryTerms.push(tokens[i] + ' ' + tokens[i+1]);
  }

  const scores = {};
  for (const term of queryTerms) {
    const postings = index[term] || [];
    for (const { id, score } of postings) {
      scores[id] = (scores[id] || 0) + score;
    }
  }

  return Object.entries(scores)
    .sort((a, b) => b[1] - a[1])
    .slice(0, topK)
    .map(([id, score]) => ({ ...docs[id], score: Math.round(score * 100) / 100 }));
}
`.trim();
}

// ─── Main ─────────────────────────────────────────────────────────────────────

console.log('Building SAP knowledge base search index...\n');

// Collect all files
const allFiles = walkDir(KNOWLEDGE_DIR);
console.log(`Found ${allFiles.length} files in knowledge/`);

// Parse documents
const documents = [];
for (const f of allFiles) {
  try {
    const doc = parseDocument(f);
    // Store only metadata (not fullText) in final index doc
    documents.push({
      id: doc.id,
      title: doc.title,
      category: doc.category,
      sourceType: doc.sourceType,
      filePath: doc.filePath,
      sapIdentifiers: doc.sapIdentifiers,
      sections: doc.sections,
      // Preview text for search results
      preview: doc.fullText.slice(0, 300).replace(/\n+/g, ' ').trim()
    });
    process.stdout.write('.');
  } catch (e) {
    console.error(`\nError parsing ${f}: ${e.message}`);
  }
}
console.log(`\nParsed ${documents.length} documents`);

// Build index from full text (re-parse for full text)
console.log('Building BM25 index...');
const fullDocs = allFiles.map((f, i) => {
  const raw = readFileSync(f, 'utf8');
  return { ...documents[i], fullText: raw };
}).filter(Boolean);

const { index, avgDL, N } = buildIndex(fullDocs);
const termCount = Object.keys(index).length;
console.log(`Index built: ${termCount} terms, ${N} documents, avgDL=${Math.round(avgDL)}`);

// Assemble final index
const searchIndex = {
  meta: {
    built: new Date().toISOString(),
    docCount: N,
    termCount,
    avgDocLength: Math.round(avgDL),
    bm25: { k1: BM25_K1, b: BM25_B }
  },
  docs: documents,   // metadata only (no fullText)
  index              // inverted index: term → [{id, score}]
};

writeFileSync(OUTPUT_FILE, JSON.stringify(searchIndex, null, 2));
const sizeKB = Math.round(Buffer.byteLength(JSON.stringify(searchIndex)) / 1024);
console.log(`\nSearch index written to: ${OUTPUT_FILE}`);
console.log(`Index size: ${sizeKB} KB`);

// Print category breakdown
const categories = {};
for (const doc of documents) {
  categories[doc.category] = (categories[doc.category] || 0) + 1;
}
console.log('\nDocuments by category:');
for (const [cat, count] of Object.entries(categories).sort((a,b) => b[1]-a[1])) {
  console.log(`  ${cat}: ${count}`);
}
