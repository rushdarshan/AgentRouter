package dev.darshan.agentrouter.tools;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Calculator tool supporting basic arithmetic operations.
 * Parses expressions like "5 + 3", "100 / 4", "2 * 7".
 */
@Component
public class CalculatorTool implements Tool {

    @Override
    public String getName() {
        return "CalculatorTool";
    }

    @Override
    public String getDescription() {
        return "Performs basic arithmetic calculations (add, subtract, multiply, divide)";
    }

    @Override
    public ToolSchema getSchema() {
        return new ToolSchema()
                .addInputField("expression", FieldType.STRING, true)
                .addOutputField("result", FieldType.NUMBER, true)
                .addOutputField("expression", FieldType.STRING, true);
    }

    @Override
    public ToolCapability getCapability() {
        return ToolCapability.CALCULATOR;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        long start = System.currentTimeMillis();
        String expression = (String) params.get("expression");

        try {
            double result = evaluate(expression.trim());
            long elapsed = System.currentTimeMillis() - start;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("result", result);
            data.put("expression", expression.trim());

            return ToolResult.success(data, elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            return ToolResult.failure("Invalid expression: " + expression + " — " + e.getMessage(), elapsed);
        }
    }

    /**
     * Evaluates a simple binary arithmetic expression.
     * Supports: +, -, *, /
     * Format: "operand operator operand" (e.g., "5 + 3")
     */
    double evaluate(String expression) {
        // Try splitting by operators (with surrounding spaces for safety)
        String[] operators = {"\\+", "-", "\\*", "/"};
        String[] opSymbols = {"+", "-", "*", "/"};

        for (int i = 0; i < operators.length; i++) {
            // Use regex to split, keeping the operator
            String[] parts = expression.split("\\s*" + operators[i] + "\\s*", 2);
            if (parts.length == 2) {
                try {
                    double left = Double.parseDouble(parts[0].trim());
                    double right = Double.parseDouble(parts[1].trim());

                    return switch (opSymbols[i]) {
                        case "+" -> left + right;
                        case "-" -> left - right;
                        case "*" -> left * right;
                        case "/" -> {
                            if (right == 0) throw new ArithmeticException("Division by zero");
                            yield left / right;
                        }
                        default -> throw new IllegalArgumentException("Unknown operator");
                    };
                } catch (NumberFormatException e) {
                    // Try next operator
                }
            }
        }
        throw new IllegalArgumentException("Cannot parse expression: " + expression);
    }
}
