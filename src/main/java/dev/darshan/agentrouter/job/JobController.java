package dev.darshan.agentrouter.job;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/jobs")
public class JobController {
    private final JobService jobs;
    private final PrincipalResolver principals;
    private final ControlAdmission control;

    public JobController(JobService jobs, PrincipalResolver principals, ControlAdmission control) {
        this.jobs = jobs;
        this.principals = principals;
        this.control = control;
    }

    @PostMapping
    public ResponseEntity<?> submit(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                    @RequestBody JobRequest request) {
        Optional<String> principal = principals.resolve(authorization);
        if (principal.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("outcome", "REJECTED", "reasonCode", "UNAUTHORIZED"));
        AcceptanceResult result = jobs.submit(principal.get(), key, request);
        if (result.status() == 202) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .location(java.net.URI.create("/jobs/" + request.operationId()))
                    .body(result.representation());
        }
        if (result.status() == 200) return ResponseEntity.ok(result.representation());
        HttpHeaders headers = new HttpHeaders();
        if (result.status() == 429 || result.status() == 503) headers.set("Retry-After", "1");
        return ResponseEntity.status(result.status()).headers(headers)
                .body(Map.of("outcome", "REJECTED", "reasonCode", result.message()));
    }

    @GetMapping("/{operationId}")
    public ResponseEntity<?> get(@RequestHeader(value = "Authorization", required = false) String authorization,
                                 @PathVariable String operationId) {
        Optional<String> principal = principals.resolve(authorization);
        if (principal.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return jobs.get(principal.get(), operationId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{operationId}/artifacts")
    public ResponseEntity<?> artifacts(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @PathVariable String operationId) {
        Optional<String> principal = principals.resolve(authorization);
        if (principal.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return jobs.artifacts(principal.get(), operationId)
                .<ResponseEntity<?>>map(artifacts -> ResponseEntity.ok(
                        Map.of("operationId", operationId, "artifacts", artifacts)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{operationId}/cancel")
    public ResponseEntity<?> cancel(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @PathVariable String operationId) {
        Optional<String> principal = principals.resolve(authorization);
        if (principal.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        // Reserved control capacity: a full control queue rejects explicitly
        // without recording a cancellation that never reached any backend.
        if (!control.tryAcquireControl()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("Retry-After", "1")
                    .body(Map.of("outcome", "REJECTED", "reasonCode", "control saturated; retry later"));
        }
        try {
            return jobs.cancel(principal.get(), operationId)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } finally {
            control.releaseControl();
        }
    }
}
