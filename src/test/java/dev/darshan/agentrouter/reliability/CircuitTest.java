package dev.darshan.agentrouter.reliability;

import dev.darshan.agentrouter.monitoring.CircuitState;
import dev.darshan.agentrouter.monitoring.ManualClock;
import dev.darshan.agentrouter.monitoring.MetricsCollector;
import dev.darshan.agentrouter.routing.CircuitBreaker;
import dev.darshan.agentrouter.tools.Tool;
import dev.darshan.agentrouter.tools.ToolResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** sci-exec-circuit: single half-open permit + generation fencing, one success closes. */
class CircuitTest {
    private static Tool failingTool() throws Exception {
        Tool tool = mock(Tool.class);
        when(tool.getName()).thenReturn("ProbeTool");
        when(tool.execute(any())).thenReturn(ToolResult.failure("boom", 1));
        return tool;
    }

    @Test
    void singleHalfOpenPermit() throws Exception {
        ManualClock clock = new ManualClock(Instant.EPOCH);
        CircuitBreaker breaker = new CircuitBreaker(new MetricsCollector(), 5, 1, 30, clock);
        Tool failing = failingTool();
        for (int i = 0; i < 5; i++) breaker.execute(failing, Map.of());
        assertEquals(CircuitState.OPEN, breaker.getState("ProbeTool"));
        clock.advanceMillis(30_000);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Tool slow = mock(Tool.class);
        when(slow.getName()).thenReturn("ProbeTool");
        AtomicInteger runs = new AtomicInteger();
        when(slow.execute(any())).thenAnswer(invocation -> {
            runs.incrementAndGet();
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return ToolResult.success(Map.of(), 1);
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ToolResult> probe = pool.submit(() -> breaker.execute(slow, Map.of()));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertThrows(CircuitBreaker.CircuitBreakerOpenException.class,
                    () -> breaker.execute(slow, Map.of()));
            release.countDown();
            assertTrue(probe.get(10, TimeUnit.SECONDS).isSuccess());
            assertEquals(1, runs.get());
            assertEquals(CircuitState.CLOSED, breaker.getState("ProbeTool"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void staleGenerationIgnored() throws Exception {
        ManualClock clock = new ManualClock(Instant.EPOCH);
        CircuitBreaker breaker = new CircuitBreaker(new MetricsCollector(), 5, 1, 30, clock);
        Tool failing = failingTool();
        for (int i = 0; i < 5; i++) breaker.execute(failing, Map.of());
        long openGeneration = breaker.getGeneration("ProbeTool");
        clock.advanceMillis(30_000);
        Tool success = mock(Tool.class);
        when(success.getName()).thenReturn("ProbeTool");
        when(success.execute(any())).thenReturn(ToolResult.success(Map.of(), 1));
        breaker.execute(success, Map.of());
        assertEquals(CircuitState.CLOSED, breaker.getState("ProbeTool"));
        for (int i = 0; i < 5; i++) breaker.execute(failing, Map.of());
        assertEquals(CircuitState.OPEN, breaker.getState("ProbeTool"));
        assertTrue(breaker.getGeneration("ProbeTool") > openGeneration);
    }
}
