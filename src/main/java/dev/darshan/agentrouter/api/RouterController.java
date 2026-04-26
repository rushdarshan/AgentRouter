package dev.darshan.agentrouter.api;

import dev.darshan.agentrouter.core.AgentRouter;
import dev.darshan.agentrouter.reporting.RouterResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing the AgentRouter pipeline.
 *
 * POST /route — Route user intent to tool and execute
 */
@RestController
@RequestMapping("/route")
public class RouterController {

    private static final Logger log = LoggerFactory.getLogger(RouterController.class);

    private final AgentRouter agentRouter;

    public RouterController(AgentRouter agentRouter) {
        this.agentRouter = agentRouter;
    }

    /**
     * Route a user request through the agent pipeline.
     *
     * @param request contains the user's natural language request
     * @return structured response with tool result or error
     */
    @PostMapping
    public ResponseEntity<RouterResponse> route(@RequestBody RouteRequest request) {
        log.info("POST /route — request: '{}'", request.getRequest());

        if (request.getRequest() == null || request.getRequest().isBlank()) {
            RouterResponse errorResponse = RouterResponse.failure(
                    "Request body must contain a non-empty 'request' field", null);
            return ResponseEntity.badRequest().body(errorResponse);
        }

        RouterResponse response = agentRouter.route(request.getRequest());

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }
}
