package dev.darshan.agentrouter.reliability;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * sci-config-v1 selection: exactly one credential mechanism (sci-jwt, never
 * mTLS) and one canonicalization profile (sci-c14n-v1); anything else fails
 * startup validation. Control capacity defaults to 8 reserved slots.
 */
class ConfigSelectionTest {
    private static SciConfig withAuth(String mechanism) {
        return new SciConfig("sci-config-v1", 16, 4, 64, 1, 4, 8, 3, 20,
                Duration.ofMillis(500), Duration.ofHours(168), Duration.ofHours(24),
                mechanism, "sci-c14n-v1");
    }

    @Test
    void defaultsSelectJwtAndEightControlSlots() {
        SciConfig defaults = SciConfig.defaults();
        assertEquals("sci-jwt", defaults.authMechanism());
        assertEquals(8, defaults.controlCapacity());
        assertDoesNotThrow(() -> SciConfigValidator.validate(defaults));
        assertDoesNotThrow(() -> new dev.darshan.agentrouter.job.ControlAdmission());
    }

    @Test
    void alternativeMechanismRejectedAtStartup() {
        assertThrows(IllegalArgumentException.class,
                () -> SciConfigValidator.validate(withAuth("mtls")));
        assertThrows(IllegalArgumentException.class,
                () -> SciConfigValidator.validate(withAuth("sci-jwt-and-mtls")));
    }
}
