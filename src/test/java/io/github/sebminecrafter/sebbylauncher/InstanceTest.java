package io.github.sebminecrafter.sebbylauncher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstanceTest {
    @Test
    void sanitizeNameTrimsAndReplacesInvalidPathCharacters() {
        assertEquals("bad_name_one", Instance.sanitizeName("  bad/name:one  "));
    }

    @Test
    void sanitizeNameUsesFallbackForNullOrBlankNames() {
        assertEquals("Instance", Instance.sanitizeName(null));
        assertEquals("Instance", Instance.sanitizeName("  "));
    }

    @Test
    void sanitizeNameLimitsNamesTo64Characters() {
        assertEquals(64, Instance.sanitizeName("a".repeat(80)).length());
    }
}
