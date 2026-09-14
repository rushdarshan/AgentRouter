// Offline JUnit executor for fault tests (no Maven).
// Recompiles the envelope test sources against target/classes, runs the named
// classes via JUnitRunner, writes artifacts/operating-envelope/raw/junit.json.
// Prints "junit verification passed" only when every test passes.
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync, existsSync } from 'node:fs';
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
  'org/junit/jupiter/junit-jupiter-api/5.10.2/junit-jupiter-api-5.10.2.jar',
  'org/junit/jupiter/junit-jupiter-engine/5.10.2/junit-jupiter-engine-5.10.2.jar',
  'org/junit/jupiter/junit-jupiter-params/5.10.2/junit-jupiter-params-5.10.2.jar',
  'org/junit/platform/junit-platform-commons/1.10.2/junit-platform-commons-1.10.2.jar',
  'org/junit/platform/junit-platform-engine/1.10.2/junit-platform-engine-1.10.2.jar',
  'org/junit/platform/junit-platform-launcher/1.10.2/junit-platform-launcher-1.10.2.jar',
  'org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar',
  'org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar',
].map((j) => join(m2, j));
for (const j of jars) if (!existsSync(j)) { console.error('FAIL: missing cached jar ' + j); process.exit(1); }

const sep = process.platform === 'win32' ? ';' : ':';
const run = (cmd, args, opts = {}) => execFileSync(cmd, args, { encoding: 'utf8', ...opts });
const classes = ['dev.darshan.agentrouter.job.ClaimContentionTest', 'dev.darshan.agentrouter.job.ExpiryZeroExecTest', 'dev.darshan.agentrouter.job.OperatingEnvelopeTest'];

try {
  const outDir = join(dir, 'out-test');
  mkdirSync(outDir, { recursive: true });
  const cp = [join(root, 'target', 'classes'), ...jars].join(sep);
  run('javac', ['-cp', cp, '-d', outDir,
    join(root, 'src', 'test', 'java', 'dev', 'darshan', 'agentrouter', 'job', 'ClaimContentionTest.java'),
    join(root, 'src', 'test', 'java', 'dev', 'darshan', 'agentrouter', 'job', 'ExpiryZeroExecTest.java'),
    join(root, 'src', 'test', 'java', 'dev', 'darshan', 'agentrouter', 'job', 'OperatingEnvelopeTest.java'),
    join(dir, 'src', 'JUnitRunner.java')]);
  const stdout = run('java', ['-cp', [outDir, cp].join(sep), 'envelope.JUnitRunner', ...classes], { cwd: root, timeout: 300000 });
  const res = JSON.parse(stdout.slice(stdout.indexOf('{')));
  const rawDir = join(root, 'artifacts', 'operating-envelope', 'raw');
  mkdirSync(rawDir, { recursive: true });
  writeFileSync(join(rawDir, 'junit.json'), JSON.stringify({ command: 'node scripts/envelope/run-junit.mjs', classes, ...res }, null, 2) + '\n');
  console.log('junit verification passed');
} catch (e) {
  console.error('FAIL: ' + (e.message || e).toString().split('\n').slice(0, 8).join(' | '));
  process.exit(1);
}
