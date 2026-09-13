package dev.darshan.agentrouter.tools;

import dev.darshan.agentrouter.monitoring.Clock;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Calculator tool supporting basic arithmetic operations.
 * Parses expressions like "5 + 3", "100 / 4", "2 * 7".
 */
@Component
public class CalculatorTool implements Tool {
    private final Clock clock;

    public CalculatorTool() {
        this(Clock.system());
    }

    public CalculatorTool(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

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
        long start = clock.monotonicNanos();
        Object rawExpression = params == null ? null : params.get("expression");
        String expression = rawExpression instanceof String ? (String) rawExpression : "";

        try {
            double result = evaluate(expression.trim());
            long elapsed = clock.elapsedMillis(start);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("result", result);
            data.put("expression", expression.trim());

            return ToolResult.success(data, elapsed);
        } catch (Exception e) {
            long elapsed = clock.elapsedMillis(start);
            String reason = e instanceof ArithmeticException ? e.getMessage() : "Invalid arithmetic expression";
            return ToolResult.failure(reason == null ? "Invalid arithmetic expression" : reason, elapsed);
        }
    }

    /**
     * Evaluates a simple binary arithmetic expression.
     * Supports: +, -, *, /
     * Format: "operand operator operand" (e.g., "5 + 3")
     */
    double evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("expression is required");
        }
        Parser parser = new Parser(expression);
        double value = parser.parseExpression();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException("unexpected trailing input");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("result is not finite");
        }
        return value;
    }

    private static final class Parser {
        private final String input;
        private int position;

        private Parser(String input) {
            this.input = input;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                skipWhitespace();
                if (consume('+')) value += parseTerm();
                else if (consume('-')) value -= parseTerm();
                else return value;
            }
        }

        private double parseTerm() {
            double value = parseFactor();
            while (true) {
                skipWhitespace();
                if (consume('*')) value *= parseFactor();
                else if (consume('/')) {
                    double divisor = parseFactor();
                    if (divisor == 0.0) throw new ArithmeticException("Division by zero");
                    value /= divisor;
                } else return value;
            }
        }

        private double parseFactor() {
            skipWhitespace();
            if (consume('+')) return parseFactor();
            if (consume('-')) return -parseFactor();
            if (consume('(')) {
                double value = parseExpression();
                skipWhitespace();
                if (!consume(')')) throw new IllegalArgumentException("missing closing parenthesis");
                return value;
            }
            int start = position;
            while (!atEnd() && (Character.isDigit(input.charAt(position))
                    || input.charAt(position) == '.'
                    || input.charAt(position) == 'e'
                    || input.charAt(position) == 'E'
                    || input.charAt(position) == '+' && position > start
                    && (input.charAt(position - 1) == 'e' || input.charAt(position - 1) == 'E')
                    || input.charAt(position) == '-' && position > start
                    && (input.charAt(position - 1) == 'e' || input.charAt(position - 1) == 'E'))) {
                position++;
            }
            if (start == position) throw new IllegalArgumentException("expected a number");
            try {
                return Double.parseDouble(input.substring(start, position));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid number");
            }
        }

        private boolean consume(char expected) {
            if (!atEnd() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(input.charAt(position))) position++;
        }

        private boolean atEnd() {
            return position >= input.length();
        }
    }
}
