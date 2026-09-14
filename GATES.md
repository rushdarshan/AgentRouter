# Gates: operating-envelope study

OWNS: GATES.md, artifacts/operating-envelope/**, scripts/envelope/**, src/test/java/dev/darshan/agentrouter/job/OperatingEnvelopeTest.java

Scope: publish a measured single-machine operating-envelope study with diagram, claims table, reproducible bench, raw output, failure note, demo, and limits

- [x] G1: architecture diagram exists with limitation statement
  CHECK: node scripts/envelope/verify-envelope.mjs g1
  EXPECT: g1 verification passed
  EVIDENCE: exit=0; shell=C:\WINDOWS\system32\cmd.exe; cwd=C:\Users\rushd\Downloads\sgent\AgentRouter; path=5a26aae74546/145 entries; output=g1 verification passed

- [x] G2: claims table links every claim to an executed test with evidence
  CHECK: node scripts/envelope/verify-envelope.mjs g2
  EXPECT: g2 verification passed
  EVIDENCE: exit=0; shell=C:\WINDOWS\system32\cmd.exe; cwd=C:\Users\rushd\Downloads\sgent\AgentRouter; path=5a26aae74546/145 entries; output=g2 verification passed

- [x] G3: benchmark command reproduces offline and writes raw output
  CHECK: node scripts/envelope/verify-envelope.mjs g3
  EXPECT: g3 verification passed
  EVIDENCE: exit=0; shell=C:\WINDOWS\system32\cmd.exe; cwd=C:\Users\rushd\Downloads\sgent\AgentRouter; path=5a26aae74546/145 entries; output=g3 verification passed

- [x] G4: raw output contains measured numbers, sample sizes, and environment
  CHECK: node scripts/envelope/verify-envelope.mjs g4
  EXPECT: g4 verification passed
  EVIDENCE: exit=0; shell=C:\WINDOWS\system32\cmd.exe; cwd=C:\Users\rushd\Downloads\sgent\AgentRouter; path=5a26aae74546/145 entries; output=g4 verification passed

- [x] G5: failure note states what fails first with controls honored
  CHECK: node scripts/envelope/verify-envelope.mjs g5
  EXPECT: g5 verification passed
  EVIDENCE: exit=0; shell=C:\WINDOWS\system32\cmd.exe; cwd=C:\Users\rushd\Downloads\sgent\AgentRouter; path=5a26aae74546/145 entries; output=g5 verification passed

- [x] G6: five-minute demo steps reference real commands and files
  CHECK: node scripts/envelope/verify-envelope.mjs g6
  EXPECT: g6 verification passed
  EVIDENCE: exit=0; shell=C:\WINDOWS\system32\cmd.exe; cwd=C:\Users\rushd\Downloads\sgent\AgentRouter; path=5a26aae74546/145 entries; output=g6 verification passed

- [x] G7: README publication stays honest (no equivalence claim, dated results)
  CHECK: node scripts/envelope/verify-envelope.mjs g7
  EXPECT: g7 verification passed
  EVIDENCE: exit=0; shell=C:\WINDOWS\system32\cmd.exe; cwd=C:\Users\rushd\Downloads\sgent\AgentRouter; path=5a26aae74546/145 entries; output=g7 verification passed
