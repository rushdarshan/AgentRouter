package dev.darshan.agentrouter.api;

import dev.darshan.agentrouter.AgentRouterApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spring Boot integration test for REST endpoints.
 */
@SpringBootTest(classes = AgentRouterApplication.class)
@AutoConfigureMockMvc
class RouterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /route with weather query returns success")
    void testRouteWeather() throws Exception {
        mockMvc.perform(post("/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\": \"What's the weather in SF?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.intent").value("weather_query"))
                .andExpect(jsonPath("$.tool").value("WeatherTool"));
    }

    @Test
    @DisplayName("POST /route with empty request returns 400")
    void testRouteEmptyRequest() throws Exception {
        mockMvc.perform(post("/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /route with unclassifiable request returns 400")
    void testRouteUnclassifiable() throws Exception {
        mockMvc.perform(post("/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\": \"xyzabc gibberish\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /health returns healthy status")
    void testHealth() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.active_service").value(true))
                .andExpect(jsonPath("$.queue_depth").isNumber())
                .andExpect(jsonPath("$.queue_capacity").value(4));
    }

    @Test
    @DisplayName("POST /route with calculator query returns result")
    void testRouteCalculator() throws Exception {
        mockMvc.perform(post("/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\": \"calculate 5 + 3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.tool").value("CalculatorTool"));
    }

    @Test
    @DisplayName("POST /route with CRM query returns customer data")
    void testRouteCRM() throws Exception {
        mockMvc.perform(post("/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\": \"get customer C001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.tool").value("CRMQueryTool"));
    }
}
