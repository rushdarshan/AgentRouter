package dev.darshan.agentrouter.tools;

import java.util.List;

/**
 * Defines a single field in a tool's input/output schema.
 * Used by SchemaValidator to enforce contracts before execution.
 */
public class FieldDefinition {

    private final String name;
    private final FieldType type;
    private final boolean required;
    private final Object defaultValue;
    private final List<Object> allowedValues;

    public FieldDefinition(String name, FieldType type, boolean required) {
        this(name, type, required, null, null);
    }

    public FieldDefinition(String name, FieldType type, boolean required,
                           Object defaultValue, List<Object> allowedValues) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.defaultValue = defaultValue;
        this.allowedValues = allowedValues;
    }

    public String getName() {
        return name;
    }

    public FieldType getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public List<Object> getAllowedValues() {
        return allowedValues;
    }
}
