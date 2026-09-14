// Reproducible benchmark command (deterministic tier, offline).
// Compiles scripts/envelope/src/EnvelopeBench.java against target/classes plus
// cached .m2 jars, runs it, merges environment, validates, writes raw output.
// Prints "bench verification passed" only after every self-check holds.
import { execFileSync, execSync } from 'node:child_process';
import { mkdirSync, writeFileSync, readFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import os from 'node:os';

const dir = dirname(fileURLToPath(import.meta.url));
const root = join(dir, '..', '..');
const m2 = join(os.homedir(), '.m2', 'repository');
const jars = [
  'org/xerial/sqlite-jdbc/3.45.3.0/sqlite-jdbc-3.45.3.0.jar',
  'com/fasterxml/jackson/core/jackson-databind/2.15.4/jackson-databind-2.15.4.jar',
  'com/fasterxml/jackson/core/jackson-core/2.15.4/jackson-core-2.15.4.jar',
  'com/fasterxml/jackson/core/jackson-annotations/2.15.4/jackson-annotations-2.15.4.jar',
  'org/slf4j/slf4j-api/2.0.13/slf4j-api-2.0.13.jar',
].map((j) => join(m2, j));
for (const j of jars) if (!existsSync(j)) { console.error('FAIL: missing cached jar ' + j); process.exit(1); }

const run = (cmd, args, opts = {}) => execFileSync(cmd, args, { encoding: 'utf8', ...opts });
const full = process.argv.includes('--full');
const outDir = join(dir, 'out');
mkdirSync(outDir, { recursive: true });
const cp = [join(root, 'target', 'classes'), ...jars].join(process.platform === 'win32' ? ';' : ':');

try {
  run('javac', ['-cp', cp, '-d', outDir, join(dir, 'src', 'EnvelopeBench.java')]);
  const stdout = run('java', ['-cp', [outDir, cp].join(process.platform === 'win32' ? ';' : ':'), 'envelope.EnvelopeBench', ...(full ? ['--full'] : [])], { cwd: root, timeout: 300000 });
  const measured = JSON.parse(stdout.slice(stdout.indexOf('{'), stdout.lastIndexOf('}') + 1));
  if (!Array.isArray(measured.levels) || measured.levels.length === 0) { console.error('FAIL: empty levels'); process.exit(1); }
  const commit = run('git', ['-C', root, 'rev-parse', 'HEAD']).trim();
  const java = execSync('java -version 2>&1', { encoding: 'utf8' }).split('\n')[0].trim();
  const rawDir = join(root, 'artifacts', 'operating-envelope', 'raw');
  mkdirSync(rawDir, { recursive: true });
  const doc = { command: 'node scripts/envelope/run-bench.mjs' + (full ? ' --full' : ''), env: { commit, java, os: os.platform() + ' ' + os.release(), arch: os.arch(), cpus: os.cpus().length }, ...measured };
  writeFileSync(join(rawDir, 'envelope-deterministic.json'), JSON.stringify(doc, null, 2) + '\n');
  console.log('bench verification passed');
} catch (e) {
  console.error('FAIL: ' + (e.message || e).toString().split('\n').slice(0, 5).join(' | '));
  process.exit(1);
}
