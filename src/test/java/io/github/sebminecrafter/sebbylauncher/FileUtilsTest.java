package io.github.sebminecrafter.sebbylauncher;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void convertsJpegImagesToPng() throws IOException {
        Path source = temporaryDirectory.resolve("source.jpg");
        Path target = temporaryDirectory.resolve("instance").resolve("logo.png");
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        Assertions.assertTrue(ImageIO.write(image, "jpg", source.toFile()));

        FileUtils.copyImageAsPng(source, target);

        assertNotNull(ImageIO.read(target.toFile()));
        byte[] encoded = Files.readAllBytes(target);
        assertArrayEquals(new byte[] {
                (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a
        }, java.util.Arrays.copyOf(encoded, 8));
    }

    @Test
    void rejectsUnsupportedImageFiles() throws IOException {
        Path source = temporaryDirectory.resolve("not-an-image.txt");
        Files.writeString(source, "not an image");

        assertThrows(IOException.class,
                () -> FileUtils.copyImageAsPng(source, temporaryDirectory.resolve("logo.png")));
    }
}
