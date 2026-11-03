package io.github.sebminecrafter.sebbylauncher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileUtilsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void deletesAnOrdinaryTemporaryDirectoryTree() throws IOException {
        Path target = Files.createDirectory(temporaryDirectory.resolve("delete-me"));
        Files.createDirectory(target.resolve("nested"));

        assertDoesNotThrow(() -> FileUtils.deleteTree(target));
        assertFalse(Files.exists(target));
    }

    @Test
    void recursiveDeletionRejectsFilesystemRoot() {
        Path root = Path.of("").toAbsolutePath().getRoot();
        assertThrows(IOException.class, () -> FileUtils.deleteTree(root));
    }

    @Test
    void rejectsOperatingSystemDirectory() {
        Path protectedDirectory;
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.startsWith("windows")) {
            protectedDirectory = Path.of(System.getenv("SystemRoot"));
        } else if (os.contains("mac")) {
            protectedDirectory = Path.of("/System");
        } else {
            protectedDirectory = Path.of("/etc");
        }
        assertThrows(IOException.class, () -> FileUtils.validateDeleteTarget(protectedDirectory));
    }

    @Test
    void instanceScopedDeletionAllowsOnlyTheSelectedInstanceFolder() {
        Path selectedInstance = LauncherDirs.INSTANCES.resolve("FileUtilsTest");
        Path siblingInstance = LauncherDirs.INSTANCES.resolve("OtherInstance");

        assertDoesNotThrow(() -> FileUtils.validateDeleteTarget(selectedInstance, selectedInstance));
        assertThrows(IOException.class, () -> FileUtils.validateDeleteTarget(siblingInstance, selectedInstance));
    }
}
