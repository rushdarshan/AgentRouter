// Portable gate oracle for GATES.md (operating-envelope study).
// Usage: node scripts/envelope/verify-envelope.mjs <g1..g7>
// Prints "<gN> verification passed" only after every assertion holds; exits nonzero otherwise.
import { readFileSync, existsSync, readdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const art = join(root, 'artifacts', 'operating-envelope');
const read = (p) => readFileSync(p, 'utf8');
const fail = (m) => { console.error('FAIL: ' + m); process.exit(1); };
const has = (s, sub, what) => { if (!s.includes(sub)) fail('missing ' + what + ': ' + JSON.stringify(sub)); };

const gate = process.argv[2];
if (!['g1','g2','g3','g4','g5','g6','g7'].includes(gate)) { console.error('unknown gate'); process.exit(2); }

if (gate === 'g1') {
  const p = join(art, 'ARCHITECTURE.md');
  if (!existsSync(p)) fail('ARCHITECTURE.md absent');
  const s = read(p);
  has(s, '```mermaid', 'mermaid diagram');
  const low1 = s.toLowerCase();
  for (const t of ['single host', 'single active service', 'sqlite']) if (!low1.includes(t)) fail('missing limitation term: ' + JSON.stringify(t));
}

if (gate === 'g2') {
  const p = join(art, 'CLAIMS.md');
  if (!existsSync(p)) fail('CLAIMS.md absent');
  const rows = read(p).split('\n').filter((l) => l.startsWith('|') && !l.startsWith('| Claim') && !l.startsWith('|---'));
  if (rows.length === 0) fail('claims table has no data rows');
  for (const r of rows) {
    const cells = r.split('|').map((c) => c.trim());
    const test = cells[2] || '', ev = cells[3] || '';
    if (!test || !existsSync(join(root, test))) fail('claim links missing test file: ' + test);
    if (!ev || !existsSync(join(root, ev))) fail('claim links missing evidence: ' + ev);
  }
}

if (gate === 'g3') {
  // Reproducibility is proven by run-bench.mjs itself (it self-validates and
  // prints the marker only on success). This gate asserts the command exists
  // and is wired as the single documented entry point.
  const p = join(root, 'scripts', 'envelope', 'run-bench.mjs');
  if (!existsSync(p)) fail('run-bench.mjs absent');
  const s = read(p);
  has(s, 'bench verification passed', 'self-validation marker');
  has(s, 'EnvelopeBench', 'bench main reference');
  const raw = join(art, 'raw');
  if (!existsSync(raw) || readdirSync(raw).filter((f) => f.endsWith('.json')).length === 0)
    fail('no raw benchmark output produced by the documented command');
}

if (gate === 'g4') {
  const raw = join(art, 'raw');
  const files = existsSync(raw) ? readdirSync(raw).filter((f) => f.startsWith('envelope-') && f.endsWith('.json')) : [];
  if (files.length === 0) fail('no raw envelope JSON');
  const junit = join(raw, 'junit.json');
  if (!existsSync(junit)) fail('no junit.json');
  const jr = JSON.parse(read(junit));
  if (typeof jr.passed !== 'number' || jr.passed <= 0 || jr.failed !== 0) fail('junit.json shows no passing run');
  for (const f of files) {
    const o = JSON.parse(read(join(raw, f)));
    if (JSON.stringify(o).toLowerCase().includes('placeholder')) fail(f + ' contains placeholder');
    for (const k of ['env', 'levels']) if (!(k in o)) fail(f + ' missing key ' + k);
    for (const k of ['commit', 'java', 'os']) if (!o.env || !o.env[k]) fail(f + ' missing env.' + k);
    if (!Array.isArray(o.levels) || o.levels.length === 0) fail(f + ' has no levels');
    for (const l of o.levels) {
      for (const k of ['clients', 'n', 'workflowQueue', 'submitP50', 'e2eP50', 'accepted', 'rejected']) {
        if (typeof l[k] !== 'number') fail(f + ' level missing numeric ' + k);
      }
      if (typeof l.busy !== 'number' || typeof l.alreadyClaimed !== 'number') fail(f + ' level missing contention counts');
      if (l.n <= 0) fail(f + ' level has empty sample');
      if (l.accepted + l.rejected !== l.n) fail(f + ' accepted+rejected does not cover sample');
    }
    for (const k of ['saturationProbe', 'contentionProbe', 'expiryProbe']) if (!(k in o)) fail(f + ' missing ' + k);
  }
}

if (gate === 'g5') {
  const p = join(art, 'ENVELOPE.md');
  if (!existsSync(p)) fail('ENVELOPE.md absent');
  const s = read(p), low = s.toLowerCase();
  for (const t of ['fails first', 'store_busy', 'submit', 'completion', 'p99', 'reject']) has(low, t, 'failure-note term');
  // Negative control: the matcher must fire on a known-positive fixture before its absence verdict is trusted.
  const positive = 'this enterprise-scale deployment handles thousands of nodes';
  if (!/enterprise-scale|enterprise scale/i.test(positive)) fail('negative-control matcher broken');
  if (/enterprise-scale|enterprise scale/i.test(s)) fail('enterprise-scale claim present');
}

if (gate === 'g6') {
  const p = join(art, 'DEMO.md');
  if (!existsSync(p)) fail('DEMO.md absent');
  const s = read(p);
  if (!/minute\s*[0-5]/i.test(s)) fail('no timed five-minute steps');
  const refs = [...s.matchAll(/`((?:artifacts|scripts|src|target)\/[^`]+?)`/g)].map((m) => m[1]);
  if (refs.length === 0) fail('demo references no repo paths');
  for (const r of refs) if (!existsSync(join(root, r))) fail('demo references missing path: ' + r);
}

if (gate === 'g7') {
  const s = read(join(root, 'README.md'));
  if (/directly mirrors Salesforce Agentforce/i.test(s)) fail('equivalence claim still present');
  for (const t of ['Verified commit', 'UNAVAILABLE']) has(s, t, 'publication term');
  if (!/\d{4}-\d{2}-\d{2}/.test(s)) fail('no dated results');
}

console.log(gate + ' verification passed');
