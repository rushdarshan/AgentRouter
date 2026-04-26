package dev.darshan.agentrouter.validation;

import dev.darshan.agentrouter.tools.FieldDefinition;
import dev.darshan.agentrouter.tools.FieldType;
import dev.darshan.agentrouter.tools.ToolSchema;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Validates tool input against its declared schema.
 * Pure gate — no mutation, only pass or fail.
 *
 * Checks performed:
 * 1. Required fields are present and non-null
 * 2. Field types match expected types
 * 3. Values are within allowed enum constraints
 */
@Component
public class SchemaValidator {

    /**
     * Validate input parameters against a tool schema.
     *
     * @param input  the parameters to validate
     * @param schema the schema defining expected fields
     * @throws ValidationException if any validation check fails
     */
    public void validate(Map<String, Object> input, ToolSchema schema) throws ValidationException {
        if (input == null) {
            throw new ValidationException("input", "Input parameters cannot be null");
        }

        for (Map.Entry<String, FieldDefinition> entry : schema.getInputFields().entrySet()) {
            String fieldName = entry.getKey();
            FieldDefinition field = entry.getValue();

            Object value = input.get(fieldName);

            // 1. Required field check
            if (field.isRequired() && value == null) {
                throw new ValidationException(fieldName, "Required field is missing");
            }

            // Skip further checks if value is null (optional field)
            if (value == null) continue;

            // 2. Type check
            validateType(fieldName, value, field.getType());

            // 3. Allowed values check (enum constraint)
            if (field.getAllowedValues() != null && !field.getAllowedValues().isEmpty()) {
                validateAllowedValues(fieldName, value, field);
            }
        }
    }

    private void validateType(String fieldName, Object value, FieldType expectedType) {
        boolean valid = switch (expectedType) {
            case STRING  -> value instanceof String;
            case NUMBER  -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case OBJECT  -> value instanceof Map;
        };

        if (!valid) {
            throw new ValidationException(fieldName,
                    "Expected type " + expectedType + " but got " + value.getClass().getSimpleName());
        }
    }

    private void validateAllowedValues(String fieldName, Object value, FieldDefinition field) {
        // For string comparisons, normalize to uppercase
        Object normalizedValue = value;
        if (value instanceof String) {
            normalizedValue = ((String) value).toUpperCase();
        }

        boolean found = false;
        for (Object allowed : field.getAllowedValues()) {
            Object normalizedAllowed = allowed;
            if (allowed instanceof String) {
                normalizedAllowed = ((String) allowed).toUpperCase();
            }
            if (normalizedValue.equals(normalizedAllowed)) {
                found = true;
                break;
            }
        }

        if (!found) {
            throw new ValidationException(fieldName,
                    "Value '" + value + "' is not in allowed values: " + field.getAllowedValues());
        }
    }
}
