package dev.darshan.agentrouter.job;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Control-gated cancel endpoint: full control queue is an explicit 503, never a phantom cancel. */
class JobControllerTest {
    private static String memoryUrl() {
        return "jdbc:sqlite:file:controller-" + UUID.randomUUID() + "?mode=memory&cache=shared";
    }

    @Test
    void fullControlQueueRejectsCancelExplicitly() throws Exception {
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl())) {
            JobService jobs = new JobService(store, PrincipalResolver.forTests());
            String id = UUID.randomUUID().toString();
            jobs.submit("operator", "k1", new JobRequest(id, "roofit", Map.of(), null));
            ControlAdmission control = new ControlAdmission(1);
            assertTrue(control.tryAcquireControl());
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                    new JobController(jobs, PrincipalResolver.forTests(), control)).build();
            mockMvc.perform(post("/jobs/" + id + "/cancel")
                            .header("Authorization", "Bearer operator")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "1"))
                    .andExpect(jsonPath("$.outcome").value("REJECTED"));
            assertFalse(jobs.get("operator", id).orElseThrow().cancellationRequested());
            control.releaseControl();
            mockMvc.perform(post("/jobs/" + id + "/cancel")
                            .header("Authorization", "Bearer operator")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
            assertTrue(jobs.get("operator", id).orElseThrow().cancellationRequested());
        }
    }
}
