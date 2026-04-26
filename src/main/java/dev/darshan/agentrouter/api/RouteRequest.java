package dev.darshan.agentrouter.api;

import java.util.Map;

/**
 * Request DTO for POST /route endpoint.
 */
public class RouteRequest {

    private String request;
    private Map<String, Object> metadata;

    public RouteRequest() {}

    public RouteRequest(String request) {
        this.request = request;
    }

    public RouteRequest(String request, Map<String, Object> metadata) {
        this.request = request;
        this.metadata = metadata;
    }

    public String getRequest() {
        return request;
    }

    public void setRequest(String request) {
        this.request = request;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
