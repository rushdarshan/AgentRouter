# artifacts/scientific-workflow-reliability-v1

One `<run-id>` per reproducible run. Publish is staging→finalize, never mutate after publish.

```
artifacts/scientific-workflow-reliability-v1/<run-id>/
  manifest.json  # sci-manifest-v1
  events.jsonl   # intent+observed
  final-state.json
  frozen-input.json
  guard-results.json
  report.txt
  artifacts/         # outputs
  inventory.sha256
```

Freeze recipe/config before exec, evaluation inputs after collect. Self-exclude only `manifest.json.sha256`; other `*.sha256` are ordinary artifacts.
