package io.github.sebminecrafter.sebbylauncher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InstanceTest {
    @Test
    void olderInstanceFilesRemainCompatibleWithoutLoaderVersion() {
        Instance instance = new com.google.gson.Gson().fromJson(
                "{\"name\":\"Test\",\"version\":\"1.20.1\",\"loader\":\"Quilt\"}", Instance.class);

        assertNull(instance.loaderVersion());
    }

    @Test
    void existingConstructorDefaultsLoaderVersionToLatest() {
        Instance instance = new Instance("Test", "1.20.1", "Quilt", "", 2048,
                java.util.List.of(), "");

        assertNull(instance.loaderVersion());
    }

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
