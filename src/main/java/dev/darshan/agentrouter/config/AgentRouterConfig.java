package dev.darshan.agentrouter.config;

import dev.darshan.agentrouter.routing.ToolRegistry;
import dev.darshan.agentrouter.tools.CalculatorTool;
import dev.darshan.agentrouter.tools.CRMQueryTool;
import dev.darshan.agentrouter.tools.Tool;
import dev.darshan.agentrouter.tools.WeatherTool;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Spring Boot configuration that registers all tools into the ToolRegistry.
 * Auto-discovers Tool beans and registers them on startup.
 */
@Configuration
public class AgentRouterConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentRouterConfig.class);

    private final ToolRegistry registry;
    private final List<Tool> tools;

    public AgentRouterConfig(ToolRegistry registry, List<Tool> tools) {
        this.registry = registry;
        this.tools = tools;
    }

    @PostConstruct
    public void registerTools() {
        tools.forEach(tool -> {
            registry.register(tool);
            log.info("Registered tool: {} [{}]", tool.getName(), tool.getCapability());
        });
        log.info("AgentRouter initialized with {} tools", registry.size());
    }
}
