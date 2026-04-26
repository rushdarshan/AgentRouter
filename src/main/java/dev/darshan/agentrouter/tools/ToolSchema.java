package dev.darshan.agentrouter.tools;

import java.util.*;

/**
 * Defines the input/output contract for a Tool.
 * Used by SchemaValidator to gate execution — no valid input, no execution.
 *
 * Builder-style API for fluent schema construction:
 * <pre>
 *   new ToolSchema()
 *       .addInputField("location", FieldType.STRING, true)
 *       .addOutputField("temp", FieldType.NUMBER, false);
 * </pre>
 */
public class ToolSchema {

    private final Map<String, FieldDefinition> inputFields;
    private final Map<String, FieldDefinition> outputFields;

    public ToolSchema() {
        this.inputFields = new LinkedHashMap<>();
        this.outputFields = new LinkedHashMap<>();
    }

    // --- Builder methods ---

    public ToolSchema addInputField(String name, FieldType type, boolean required) {
        inputFields.put(name, new FieldDefinition(name, type, required));
        return this;
    }

    public ToolSchema addInputField(String name, FieldType type, boolean required,
                                    Object defaultValue, List<Object> allowedValues) {
        inputFields.put(name, new FieldDefinition(name, type, required, defaultValue, allowedValues));
        return this;
    }

    public ToolSchema addOutputField(String name, FieldType type, boolean required) {
        outputFields.put(name, new FieldDefinition(name, type, required));
        return this;
    }

    // --- Accessors ---

    public Map<String, FieldDefinition> getInputFields() {
        return Collections.unmodifiableMap(inputFields);
    }

    public Map<String, FieldDefinition> getOutputFields() {
        return Collections.unmodifiableMap(outputFields);
    }
}
