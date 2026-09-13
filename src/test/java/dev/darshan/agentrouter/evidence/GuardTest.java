package dev.darshan.agentrouter.evidence;

import org.junit.jupiter.api.Test;

import static dev.darshan.agentrouter.evidence.GuardEvaluator.GuardInput;
import static dev.darshan.agentrouter.evidence.GuardEvaluator.GuardResult;
import static dev.darshan.agentrouter.evidence.GuardEvaluator.GuardVerdict;
import static org.junit.jupiter.api.Assertions.*;

/** sci-ev-guard: divergence, insufficiency, rejection, crash, runner mapping. */
class GuardTest {
    private final GuardEvaluator guard = new GuardEvaluator();

    @Test
    void divergenceExpectedFail() {
        GuardResult result = guard.evaluate(
                new GuardInput(false, false, "SUCCESS", "FAILED", true), GuardVerdict.FAIL);
        assertEquals(GuardVerdict.FAIL, result.verdict());
        assertEquals(0, result.runnerExit());
    }

    @Test
    void consistentTerminalPairPasses() {
        GuardResult result = guard.evaluate(
                new GuardInput(false, false, "SUCCESS", "COMPLETED", true), GuardVerdict.PASS);
        assertEquals(GuardVerdict.PASS, result.verdict());
        assertEquals(0, result.runnerExit());
    }

    @Test
    void failurePairPasses() {
        GuardResult result = guard.evaluate(
                new GuardInput(false, false, "EXECUTION_FAILURE", "FAILED", true), GuardVerdict.PASS);
        assertEquals(GuardVerdict.PASS, result.verdict());
        assertEquals(0, result.runnerExit());
    }

    @Test
    void nonTerminalPairDiverges() {
        GuardResult result = guard.evaluate(
                new GuardInput(false, false, "SUCCESS", "RUNNING", true), GuardVerdict.FAIL);
        assertEquals(GuardVerdict.FAIL, result.verdict());
        assertEquals(0, result.runnerExit());
    }

    @Test
    void insufficient() {
        GuardResult result = guard.evaluate(
                new GuardInput(false, false, "SUCCESS", "COMPLETED", false), GuardVerdict.INSUFFICIENT);
        assertEquals(GuardVerdict.INSUFFICIENT, result.verdict());
        assertEquals(0, result.runnerExit());
    }

    @Test
    void malformedRejected() {
        GuardResult result = guard.evaluate(
                new GuardInput(true, false, "SUCCESS", "COMPLETED", true), GuardVerdict.FAIL);
        assertEquals(GuardVerdict.REJECTED, result.verdict());
        assertEquals(1, result.runnerExit());
    }

    @Test
    void crashError() {
        GuardResult result = guard.evaluate(
                new GuardInput(false, true, "SUCCESS", "COMPLETED", true), GuardVerdict.PASS);
        assertEquals(GuardVerdict.ERROR, result.verdict());
        assertEquals(1, result.runnerExit());
    }

    @Test
    void cancelVsCompletion() {
        GuardResult result = guard.evaluate(
                new GuardInput(false, false, "CANCELLED", "COMPLETED", true), GuardVerdict.FAIL);
        assertEquals(GuardVerdict.FAIL, result.verdict());
        assertEquals(0, result.runnerExit());
    }

    @Test
    void artifactInsufficient() {
        GuardResult result = guard.evaluate(
                new GuardInput(false, false, "SUCCESS", "FAILED", false), GuardVerdict.INSUFFICIENT);
        assertEquals(GuardVerdict.INSUFFICIENT, result.verdict());
    }
}
