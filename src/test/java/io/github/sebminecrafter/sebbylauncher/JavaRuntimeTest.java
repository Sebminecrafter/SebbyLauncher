package io.github.sebminecrafter.sebbylauncher;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaRuntimeTest {
    @Test
    void parsesModernJavaVersion() throws IOException {
        assertEquals(25, JavaRuntime.parseFeatureVersion("openjdk 25.0.3 2026-04-21\n"));
    }

    @Test
    void parsesLegacyJavaVersion() throws IOException {
        assertEquals(8, JavaRuntime.parseFeatureVersion("java version \"1.8.0_402\"\n"));
    }

    @Test
    void rejectsUnrecognizedVersionOutput() {
        assertThrows(IOException.class, () -> JavaRuntime.parseFeatureVersion("not Java"));
    }

    @Test
    void reportsTheFeatureVersionOfTheCurrentRuntime() throws IOException {
        String executableName = System.getProperty("os.name").toLowerCase().contains("win")
                ? "java.exe" : "java";
        Path executable = Path.of(System.getProperty("java.home"), "bin", executableName);

        assertEquals(Runtime.version().feature(), JavaRuntime.featureVersion(executable));
    }
}
