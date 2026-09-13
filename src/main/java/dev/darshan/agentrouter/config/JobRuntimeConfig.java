package dev.darshan.agentrouter.config;

import dev.darshan.agentrouter.job.DispatchService;
import dev.darshan.agentrouter.job.DockerBackendRuntime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Production-only fixed runtime boundary; test fixtures inject their own double. */
@Configuration
@Profile("!test")
public class JobRuntimeConfig {
    @Bean
    public DispatchService dispatchService() {
        return new DispatchService(DockerBackendRuntime.configured());
    }
}
