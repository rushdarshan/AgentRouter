package dev.darshan.agentrouter.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Fixed Docker runtime boundary for the real-workflow tier. Callers cannot
 * supply an image, command, mount, or network override. If Docker or the
 * configured pinned image is unavailable, this boundary fails closed.
 */
public final class DockerBackendRuntime implements BackendRuntime {
    private final String image;
    private final String entrypoint;
    private final ObjectMapper json = new ObjectMapper();

    public DockerBackendRuntime(String image, String entrypoint) {
        if (image == null || image.isBlank() || image.equalsIgnoreCase("UNAVAILABLE")
                || !image.matches(".+@sha256:[0-9a-fA-F]{64}")) {
            throw new IllegalStateException("UNAVAILABLE: pinned RooFit image digest is not configured");
        }
        this.image = image;
        this.entrypoint = entrypoint == null || entrypoint.isBlank() ? "/bin/sh" : entrypoint;
    }

    public static DockerBackendRuntime configured() {
        return new DockerBackendRuntime(
                System.getProperty("agentrouter.roofit.image",
                        System.getenv().getOrDefault("AGENTROUTER_ROOFIT_IMAGE", "")),
                System.getProperty("agentrouter.roofit.entrypoint",
                        System.getenv().getOrDefault("AGENTROUTER_ROOFIT_ENTRYPOINT", "/bin/sh")));
    }

    @Override
    public BackendHandle createIfAbsent(String deterministicName, String operationId) {
        requireDocker();
        Optional<JsonNode> existing = inspectJson(deterministicName);
        if (existing.isPresent()) {
            String bound = existing.get().path("Config").path("Labels")
                    .path("agentrouter.operationId").asText("");
            if (!operationId.equals(bound)) throw new IllegalStateException("backend identity binding conflict");
            return new BackendHandle(deterministicName, false);
        }
        run(List.of("docker", "create", "--name", deterministicName,
                "--label", "agentrouter.operationId=" + operationId,
                "--label", "agentrouter.executionUnit=roofit",
                "--network", "none", image, entrypoint));
        // Creation is not execution. The service must explicitly start and
        // observe the fixed unit; recovery never re-runs an existing name.
        return new BackendHandle(deterministicName, true);
    }

    @Override
    public void start(String backendIdentity) {
        requireDocker();
        run(List.of("docker", "start", backendIdentity));
    }

    @Override
    public Optional<BackendObservation> inspect(String deterministicName) {
        requireDocker();
        Optional<JsonNode> node = inspectJson(deterministicName);
        if (node.isEmpty()) return Optional.empty();
        JsonNode value = node.get();
        String operationId = value.path("Config").path("Labels")
                .path("agentrouter.operationId").asText("");
        String status = value.path("State").path("Status").asText("UNKNOWN").toUpperCase();
        int exitCode = value.path("State").path("ExitCode").asInt(0);
        boolean started = value.path("State").path("StartedAt").asText("").length() > 0
                && !"CREATED".equals(status);
        // Exit code != 0 means execution failure, not completion
        if ("EXITED".equals(status) && exitCode != 0) {
            status = "FAILED";
        }
        return Optional.of(new BackendObservation(deterministicName, operationId, true, started, status, exitCode));
    }

    @Override
    public boolean cancel(String backendIdentity) {
        requireDocker();
        run(List.of("docker", "stop", "--time", "1", backendIdentity));
        return true;
    }

    private Optional<JsonNode> inspectJson(String name) {
        ProcessResult result = runAllowMissing(List.of("docker", "inspect", name));
        if (result.exitCode != 0 || result.stdout.isBlank()) return Optional.empty();
        try {
            JsonNode array = json.readTree(result.stdout);
            return array.isArray() && !array.isEmpty() ? Optional.of(array.get(0)) : Optional.empty();
        } catch (IOException e) {
            throw new IllegalStateException("malformed Docker response", e);
        }
    }

    private void requireDocker() {
        ProcessResult result = runAllowMissing(List.of("docker", "version", "--format", "{{.Server.Version}}"));
        if (result.exitCode != 0) throw new IllegalStateException("UNAVAILABLE: Docker runtime is unavailable");
    }

    private void run(List<String> command) {
        ProcessResult result = runAllowMissing(command);
        if (result.exitCode != 0) throw new IllegalStateException("Docker runtime operation failed");
    }

    private static final long DEFAULT_TIMEOUT_MS = 30_000;
    private static final int MAX_OUTPUT_BYTES = 1024 * 1024; // 1 MiB

    private ProcessResult runAllowMissing(List<String> command) {
        return runAllowMissing(command, DEFAULT_TIMEOUT_MS, MAX_OUTPUT_BYTES);
    }

    private ProcessResult runAllowMissing(List<String> command, long timeoutMs, int maxOutputBytes) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            byte[] raw = process.getInputStream().readNBytes(maxOutputBytes + 1);
            boolean truncated = raw.length > maxOutputBytes;
            byte[] output = truncated ? java.util.Arrays.copyOf(raw, maxOutputBytes) : raw;
            boolean finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                int exit = process.exitValue();
                return new ProcessResult(exit, new String(output, StandardCharsets.UTF_8));
            }
            int exit = process.exitValue();
            return new ProcessResult(exit, new String(output, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new ProcessResult(127, "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ProcessResult(130, "");
        }
    }

    private record ProcessResult(int exitCode, String stdout) {}
}
