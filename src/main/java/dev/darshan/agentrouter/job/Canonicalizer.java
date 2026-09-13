package dev.darshan.agentrouter.job;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** sci-c14n-v1: duplicate-key rejecting, UTF-8, lexicographically sorted JSON. */
public final class Canonicalizer {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private Canonicalizer() {}

    public static String canonicalize(Object value) {
        try {
            return canonicalize(MAPPER.valueToTree(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid canonical request");
        }
    }

    public static String canonicalize(String json) {
        try {
            return canonicalize(MAPPER.readTree(json));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid canonical request");
        }
    }

    private static String canonicalize(JsonNode node) {
        if (node == null || node.isNull()) return "null";
        if (node.isObject()) {
            List<String> fields = new ArrayList<>();
            node.fieldNames().forEachRemaining(fields::add);
            Collections.sort(fields);
            StringBuilder out = new StringBuilder("{");
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) out.append(',');
                String key = fields.get(i);
                out.append(quote(key)).append(':').append(canonicalize(node.get(key)));
            }
            return out.append('}').toString();
        }
        if (node.isArray()) {
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) out.append(',');
                out.append(canonicalize(node.get(i)));
            }
            return out.append(']').toString();
        }
        // sci-c14n-v1 follows RFC 8785 number spelling: -0 is 0,
        // decimal points/trailing zeroes are removed, and exponent spelling is
        // lower-case with an explicit sign for positive exponents.
        if (node.isNumber()) {
            BigDecimal number;
            try {
                number = node.decimalValue();
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Invalid canonical number");
            }
            if (number.signum() == 0) return "0";
            number = number.stripTrailingZeros();
            int exponent = number.precision() - number.scale() - 1;
            if (exponent >= -6 && exponent < 21) return number.toPlainString();
            String scientific = number.toString().replace('E', 'e');
            int marker = scientific.indexOf('e');
            if (marker >= 0 && scientific.charAt(marker + 1) != '-' && scientific.charAt(marker + 1) != '+') {
                scientific = scientific.substring(0, marker + 1) + "+"
                        + scientific.substring(marker + 1);
            }
            return scientific;
        }
        if (node.isBoolean()) return node.booleanValue() ? "true" : "false";
        return quote(node.textValue());
    }

    private static String quote(String value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid canonical string");
        }
    }

    public static String sha256(String canonicalJson) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hash(Object value) {
        return sha256(canonicalize(value));
    }
}
