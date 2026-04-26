package dev.darshan.agentrouter.core;

import dev.darshan.agentrouter.execution.ExecuteToolNode;
import dev.darshan.agentrouter.reporting.ErrorHandlerNode;
import dev.darshan.agentrouter.reporting.ReportResultNode;
import dev.darshan.agentrouter.routing.ResolveIntentNode;
import dev.darshan.agentrouter.validation.ValidateInputNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for StateGraph pipeline orchestration.
 */
@ExtendWith(MockitoExtension.class)
class StateGraphTest {

    @Mock private ResolveIntentNode resolveIntent;
    @Mock private ValidateInputNode validateInput;
    @Mock private ExecuteToolNode executeTool;
    @Mock private ReportResultNode reportResult;
    @Mock private ErrorHandlerNode errorHandler;

    private StateGraph stateGraph;

    @BeforeEach
    void setUp() {
        stateGraph = new StateGraph(resolveIntent, validateInput,
                executeTool, reportResult, errorHandler);
    }

    @Test
    @DisplayName("Happy path executes all 4 nodes in order")
    void testHappyPath() {
        ExecutionContext ctx = new ExecutionContext("weather in SF");

        when(resolveIntent.execute(any())).thenReturn(ctx);
        when(validateInput.execute(any())).thenReturn(ctx);
        when(executeTool.execute(any())).thenReturn(ctx);
        when(reportResult.execute(any())).thenReturn(ctx);

        ExecutionContext result = stateGraph.execute("weather in SF");

        verify(resolveIntent).execute(any());
        verify(validateInput).execute(any());
        verify(executeTool).execute(any());
        verify(reportResult).execute(any());
        verify(errorHandler, never()).handle(any());
    }

    @Test
    @DisplayName("Error at ResolveIntent branches to ErrorHandler")
    void testErrorAtResolveIntent() {
        ExecutionContext errorCtx = new ExecutionContext("xyzabc")
                .withError(new RuntimeException("Cannot classify"));

        when(resolveIntent.execute(any())).thenReturn(errorCtx);
        when(errorHandler.handle(any())).thenReturn(errorCtx);

        ExecutionContext result = stateGraph.execute("xyzabc");

        verify(resolveIntent).execute(any());
        verify(errorHandler).handle(any());
        verify(validateInput, never()).execute(any());
        verify(executeTool, never()).execute(any());
    }

    @Test
    @DisplayName("Error at ValidateInput branches to ErrorHandler")
    void testErrorAtValidateInput() {
        ExecutionContext goodCtx = new ExecutionContext("weather");
        ExecutionContext errorCtx = new ExecutionContext("weather")
                .withError(new RuntimeException("Validation failed"));

        when(resolveIntent.execute(any())).thenReturn(goodCtx);
        when(validateInput.execute(any())).thenReturn(errorCtx);
        when(errorHandler.handle(any())).thenReturn(errorCtx);

        stateGraph.execute("weather");

        verify(resolveIntent).execute(any());
        verify(validateInput).execute(any());
        verify(errorHandler).handle(any());
        verify(executeTool, never()).execute(any());
    }

    @Test
    @DisplayName("Error at ExecuteTool branches to ErrorHandler")
    void testErrorAtExecuteTool() {
        ExecutionContext goodCtx = new ExecutionContext("weather in SF");
        ExecutionContext errorCtx = new ExecutionContext("weather in SF")
                .withError(new RuntimeException("Tool failed"));

        when(resolveIntent.execute(any())).thenReturn(goodCtx);
        when(validateInput.execute(any())).thenReturn(goodCtx);
        when(executeTool.execute(any())).thenReturn(errorCtx);
        when(errorHandler.handle(any())).thenReturn(errorCtx);

        stateGraph.execute("weather in SF");

        verify(executeTool).execute(any());
        verify(errorHandler).handle(any());
        verify(reportResult, never()).execute(any());
    }

    @Test
    @DisplayName("Unexpected exception is caught and routed to ErrorHandler")
    void testUnexpectedException() {
        when(resolveIntent.execute(any())).thenThrow(new RuntimeException("Boom"));
        when(errorHandler.handle(any())).thenAnswer(inv -> inv.getArgument(0));

        ExecutionContext result = stateGraph.execute("anything");

        verify(errorHandler).handle(any());
        assertTrue(result.hasError());
    }
}
