package io.github.sebminecrafter.sebbylauncher;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FileUtils {
    private FileUtils() {}

    public static void deleteTree(Path root) throws IOException {
        validateDeleteTarget(root);
        deleteTreeContents(root);
    }

    public static void deleteTree(Path root, Path allowedInstanceDirectory) throws IOException {
        validateDeleteTarget(root, allowedInstanceDirectory);
        deleteTreeContents(root);
    }

    public static void validateDeleteTarget(Path target) throws IOException {
        validateDeleteTarget(target, null);
    }

    public static void validateDeleteTarget(Path target, Path allowedInstanceDirectory) throws IOException {
        Path resolvedTarget = resolveExistingPath(target.toAbsolutePath().normalize());
        Path home = resolveExistingPath(Path.of(System.getProperty("user.home")).toAbsolutePath().normalize());
        Path launcherRoot = resolveExistingPath(LauncherDirs.ROOT.toAbsolutePath().normalize());
        Path instancesDirectory = resolveExistingPath(LauncherDirs.INSTANCES.toAbsolutePath().normalize());
        Path workingDirectory = resolveExistingPath(Path.of("").toAbsolutePath().normalize());
        Path resolvedInstanceDirectory = allowedInstanceDirectory == null ? null
                : resolveExistingPath(allowedInstanceDirectory.toAbsolutePath().normalize());

        if (resolvedTarget.equals(resolvedTarget.getRoot())
                || home.startsWith(resolvedTarget)
                || launcherRoot.startsWith(resolvedTarget)
                || workingDirectory.startsWith(resolvedTarget)
                || overlapsProtectedSystemDirectory(resolvedTarget)) {
            throw unsafeTarget(target);
        }

        if (resolvedInstanceDirectory == null) {
            if (resolvedTarget.startsWith(instancesDirectory)) {
                throw unsafeTarget(target);
            }
            return;
        }

        if (!resolvedInstanceDirectory.startsWith(instancesDirectory)
                || resolvedInstanceDirectory.getParent() == null
                || !resolvedInstanceDirectory.getParent().equals(instancesDirectory)
                || (resolvedTarget.startsWith(instancesDirectory)
                        && !resolvedTarget.startsWith(resolvedInstanceDirectory))
                || (resolvedTarget.startsWith(launcherRoot)
                        && !resolvedTarget.startsWith(resolvedInstanceDirectory))
                || (resolvedInstanceDirectory.startsWith(resolvedTarget)
                        && !resolvedInstanceDirectory.equals(resolvedTarget))) {
            throw unsafeTarget(target);
        }
    }

    private static void deleteTreeContents(Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static IOException unsafeTarget(Path target) {
        return new IOException("Refusing to recursively delete a protected or overly broad folder: " + target);
    }

    private static boolean overlapsProtectedSystemDirectory(Path target) throws IOException {
        for (Path protectedPath : protectedSystemDirectories()) {
            Path resolvedProtectedPath = resolveExistingPath(protectedPath.toAbsolutePath().normalize());
            if (target.startsWith(resolvedProtectedPath) || resolvedProtectedPath.startsWith(target)) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> protectedSystemDirectories() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<Path> protectedPaths = new ArrayList<>();
        if (os.startsWith("windows")) {
            addEnvironmentPath(protectedPaths, "SystemRoot");
            addEnvironmentPath(protectedPaths, "WINDIR");
            addEnvironmentPath(protectedPaths, "ProgramFiles");
            addEnvironmentPath(protectedPaths, "ProgramFiles(x86)");
            addEnvironmentPath(protectedPaths, "ProgramW6432");
            addEnvironmentPath(protectedPaths, "ProgramData");
            addEnvironmentPath(protectedPaths, "CommonProgramFiles");
            addEnvironmentPath(protectedPaths, "CommonProgramFiles(x86)");
            String systemRoot = System.getenv("SystemRoot");
            if (systemRoot != null && !systemRoot.isBlank()) {
                Path root = Path.of(systemRoot).toAbsolutePath().getRoot();
                if (root != null) protectedPaths.add(root.resolve("Windows"));
            }
        } else if (os.contains("mac")) {
            protectedPaths.addAll(List.of(
                    Path.of("/System"), Path.of("/Library"), Path.of("/Applications"),
                    Path.of("/private"), Path.of("/usr"), Path.of("/bin"), Path.of("/sbin"),
                    Path.of("/etc"), Path.of("/var"), Path.of("/cores")));
        } else {
            protectedPaths.addAll(List.of(
                    Path.of("/bin"), Path.of("/boot"), Path.of("/dev"), Path.of("/etc"),
                    Path.of("/lib"), Path.of("/lib32"), Path.of("/lib64"), Path.of("/opt"),
                    Path.of("/proc"), Path.of("/root"), Path.of("/run"), Path.of("/sbin"),
                    Path.of("/snap"), Path.of("/sys"), Path.of("/usr"), Path.of("/var")));
        }

        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isBlank()) {
            protectedPaths.add(Path.of(javaHome));
        }
        return protectedPaths;
    }

    private static void addEnvironmentPath(List<Path> paths, String name) {
        String value = System.getenv(name);
        if (value != null && !value.isBlank()) {
            paths.add(Path.of(value));
        }
    }

    private static Path resolveExistingPath(Path path) throws IOException {
        Path existing = path;
        List<Path> remaining = new ArrayList<>();
        while (existing != null) {
            try {
                Path resolved = existing.toRealPath();
                for (Path segment : remaining) {
                    resolved = resolved.resolve(segment);
                }
                return resolved.normalize();
            } catch (NoSuchFileException e) {
                if (existing.getParent() == null) throw e;
                remaining.addFirst(existing.getFileName());
                existing = existing.getParent();
            }
        }
        throw new IOException("Could not resolve delete target safely: " + path);
    }
}
