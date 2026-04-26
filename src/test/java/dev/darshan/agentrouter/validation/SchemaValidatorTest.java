package dev.darshan.agentrouter.validation;

import dev.darshan.agentrouter.tools.FieldType;
import dev.darshan.agentrouter.tools.ToolSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SchemaValidator — field validation logic.
 */
class SchemaValidatorTest {

    private final SchemaValidator validator = new SchemaValidator();

    @Test
    @DisplayName("Valid input passes validation")
    void testValidInput() {
        ToolSchema schema = new ToolSchema()
                .addInputField("name", FieldType.STRING, true)
                .addInputField("age", FieldType.NUMBER, false);

        Map<String, Object> input = Map.of("name", "John", "age", 30);
        assertDoesNotThrow(() -> validator.validate(input, schema));
    }

    @Test
    @DisplayName("Missing required field throws ValidationException")
    void testMissingRequiredField() {
        ToolSchema schema = new ToolSchema()
                .addInputField("name", FieldType.STRING, true);

        Map<String, Object> input = new HashMap<>();
        input.put("name", null);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> validator.validate(input, schema));
        assertEquals("name", ex.getFieldName());
    }

    @Test
    @DisplayName("Wrong type throws ValidationException")
    void testWrongType() {
        ToolSchema schema = new ToolSchema()
                .addInputField("count", FieldType.NUMBER, true);

        Map<String, Object> input = Map.of("count", "not a number");

        ValidationException ex = assertThrows(ValidationException.class,
                () -> validator.validate(input, schema));
        assertTrue(ex.getMessage().contains("Expected type NUMBER"));
    }

    @Test
    @DisplayName("Value not in allowed values throws ValidationException")
    void testAllowedValues() {
        ToolSchema schema = new ToolSchema()
                .addInputField("location", FieldType.STRING, true,
                        null, List.of("SF", "NYC", "LA"));

        Map<String, Object> input = Map.of("location", "TOKYO");

        ValidationException ex = assertThrows(ValidationException.class,
                () -> validator.validate(input, schema));
        assertTrue(ex.getMessage().contains("not in allowed values"));
    }

    @Test
    @DisplayName("Null input throws ValidationException")
    void testNullInput() {
        ToolSchema schema = new ToolSchema()
                .addInputField("name", FieldType.STRING, true);

        assertThrows(ValidationException.class,
                () -> validator.validate(null, schema));
    }

    @Test
    @DisplayName("Optional field with null value passes validation")
    void testOptionalNullField() {
        ToolSchema schema = new ToolSchema()
                .addInputField("optional", FieldType.STRING, false);

        Map<String, Object> input = new HashMap<>();
        input.put("optional", null);

        assertDoesNotThrow(() -> validator.validate(input, schema));
    }

    @Test
    @DisplayName("Boolean type validation works")
    void testBooleanType() {
        ToolSchema schema = new ToolSchema()
                .addInputField("flag", FieldType.BOOLEAN, true);

        assertDoesNotThrow(() -> validator.validate(Map.of("flag", true), schema));

        assertThrows(ValidationException.class,
                () -> validator.validate(Map.of("flag", "true"), schema));
    }

    @Test
    @DisplayName("Allowed values check is case-insensitive for strings")
    void testCaseInsensitiveAllowedValues() {
        ToolSchema schema = new ToolSchema()
                .addInputField("location", FieldType.STRING, true,
                        null, List.of("SF", "NYC"));

        assertDoesNotThrow(() -> validator.validate(Map.of("location", "sf"), schema));
        assertDoesNotThrow(() -> validator.validate(Map.of("location", "Sf"), schema));
    }
}
