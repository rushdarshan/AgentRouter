package dev.darshan.agentrouter.validation;

/**
 * Thrown when tool input fails schema validation.
 * Contains details about which field(s) failed and why.
 */
public class ValidationException extends RuntimeException {

    private final String fieldName;
    private final String reason;

    public ValidationException(String fieldName, String reason) {
        super("Validation failed for field '" + fieldName + "': " + reason);
        this.fieldName = fieldName;
        this.reason = reason;
    }

    public ValidationException(String message) {
        super(message);
        this.fieldName = null;
        this.reason = message;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getReason() {
        return reason;
    }
}
