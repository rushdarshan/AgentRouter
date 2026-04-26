package dev.darshan.agentrouter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AgentRouter — Java agent tool orchestration framework.
 * Mirrors Salesforce Agentforce's orchestration model with a deterministic
 * 4+1 node pipeline: ResolveIntent → ValidateInput → ExecuteTool → ReportResult (+ErrorHandler).
 */
@SpringBootApplication
public class AgentRouterApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentRouterApplication.class, args);
    }
}
