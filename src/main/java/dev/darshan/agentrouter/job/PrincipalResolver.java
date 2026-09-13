package dev.darshan.agentrouter.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Credential-derived resolver for the selected sci-jwt contract.
 * Production accepts only a signed JWT configured with issuer and secret.
 * Literal token mappings are available solely through {@link #forTests()} or
 * an explicitly marked test/dev process.
 */
@Component
public class PrincipalResolver {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Map<String, String> testTokens;
    private final byte[] jwtSecret;
    private final String issuer;

    public PrincipalResolver() {
        String environment = firstNonBlank(System.getProperty("agentrouter.environment"),
                System.getenv("AGENTROUTER_ENV"), "production");
        String secret = firstNonBlank(System.getProperty("agentrouter.jwt.secret"),
                System.getenv("AGENTROUTER_JWT_SECRET"), "");
        String configuredIssuer = firstNonBlank(System.getProperty("agentrouter.jwt.issuer"),
                System.getenv("AGENTROUTER_JWT_ISSUER"), "");
        String rawTokens = firstNonBlank(System.getProperty("agentrouter.tokens"),
                System.getenv("AGENTROUTER_TOKENS"), "");

        if (!secret.isBlank()) {
            if (secret.length() < 32 || configuredIssuer.isBlank()) {
                throw new IllegalStateException("sci-jwt requires a 32+ character secret and issuer");
            }
            this.jwtSecret = secret.getBytes(StandardCharsets.UTF_8);
            this.issuer = configuredIssuer;
            this.testTokens = Map.of();
        } else if (("test".equalsIgnoreCase(environment) || "dev".equalsIgnoreCase(environment))
                && !rawTokens.isBlank()) {
            this.jwtSecret = null;
            this.issuer = null;
            this.testTokens = parseConfiguredTokens(rawTokens);
        } else {
            throw new IllegalStateException(
                    "sci-jwt configuration is missing; refusing startup without issuer and secret");
        }
    }

    private PrincipalResolver(Map<String, String> tokens) {
        this.testTokens = Collections.unmodifiableMap(new LinkedHashMap<>(tokens));
        this.jwtSecret = null;
        this.issuer = null;
    }

    /** Explicit literal mapping for contract-tier fixtures only. */
    public static PrincipalResolver forTests() {
        return new PrincipalResolver(Map.of("operator", "operator", "viewer", "viewer"));
    }

    public Optional<String> resolve(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return Optional.empty();
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isBlank()) return Optional.empty();
        if (jwtSecret == null) return Optional.ofNullable(testTokens.get(token));
        return verifyJwt(token);
    }

    public boolean canWrite(String principal) {
        return "operator".equals(principal);
    }

    private Optional<String> verifyJwt(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) return Optional.empty();
            String signingInput = parts[0] + "." + parts[1];
            String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    hmac(signingInput.getBytes(StandardCharsets.US_ASCII)));
            if (!MessageDigestIsEqual.constantTime(expected, parts[2])) return Optional.empty();
            JsonNode header = JSON.readTree(Base64.getUrlDecoder().decode(parts[0]));
            JsonNode claims = JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (!"HS256".equals(header.path("alg").asText())
                    || !issuer.equals(claims.path("iss").asText())) return Optional.empty();
            if (claims.has("exp") && (!claims.path("exp").canConvertToLong()
                    || Instant.now().getEpochSecond() >= claims.path("exp").asLong())) return Optional.empty();
            String subject = claims.path("sub").asText("");
            return subject.isBlank() ? Optional.empty() : Optional.of(subject);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private byte[] hmac(byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(jwtSecret, "HmacSHA256"));
        return mac.doFinal(data);
    }

    static Map<String, String> parseConfiguredTokens() {
        String raw = firstNonBlank(System.getProperty("agentrouter.tokens"),
                System.getenv("AGENTROUTER_TOKENS"), "");
        if (raw.isBlank()) throw new IllegalStateException("test token configuration is missing");
        return parseConfiguredTokens(raw);
    }

    private static Map<String, String> parseConfiguredTokens(String raw) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String pair : raw.split(",")) {
            String[] parts = pair.split(":", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalStateException("malformed test token configuration");
            }
            parsed.put(parts[0].trim(), parts[1].trim());
        }
        return parsed;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }

    private static final class MessageDigestIsEqual {
        private static boolean constantTime(String expected, String actual) {
            return java.security.MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    actual.getBytes(StandardCharsets.US_ASCII));
        }
    }
}
