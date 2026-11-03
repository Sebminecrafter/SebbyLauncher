package io.github.sebminecrafter.sebbylauncher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public record Instance(String name, String version, String loader, String image, int memoryMb,
                      List<String> jvmArgs, String gameFolder, String loaderVersion) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public Instance(String name, String version, String loader, String image, int memoryMb,
                    List<String> jvmArgs, String gameFolder) {
        this(name, version, loader, image, memoryMb, jvmArgs, gameFolder, null);
    }

    public static Instance defaultInstance() {
        Instance fallback = new Instance("Default", "latest", "Vanilla", "logo/Sebby64.png", 2048, List.of(), "");
        try {
            fallback.save();
        } catch (IOException ignored) {
            // Best effort; the launcher can still run with a temporary in-memory instance.
        }
        return fallback;
    }

    public static List<Instance> loadAll() {
        List<Instance> instances = new ArrayList<>();
        Path directory = LauncherDirs.INSTANCES;
        if (!Files.isDirectory(directory)) {
            return instances;
        }

        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isDirectory).forEach(folder -> {
                Path settingsFile = folder.resolve("instance.json");
                if (!Files.isRegularFile(settingsFile)) return;
                try {
                    Instance instance = fromJson(settingsFile);
                    if (instance != null) {
                        instances.add(instance);
                    }
                } catch (IOException ignored) {
                    // Ignore stale or malformed instance metadata until the user fixes it.
                }
            });
        } catch (IOException ignored) {
            // The instance directory may not exist yet or may be unreadable.
        }

        return instances;
    }

    public static Instance fromJson(Path settingsFile) throws IOException {
        try (var reader = Files.newBufferedReader(settingsFile)) {
            return GSON.fromJson(reader, Instance.class);
        }
    }

    public static String sanitizeName(String rawName) {
        String sanitized = rawName == null ? "" : rawName.trim();
        if (sanitized.isEmpty()) {
            sanitized = "Instance";
        }

        sanitized = sanitized.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (sanitized.length() > 64) {
            sanitized = sanitized.substring(0, 64).trim();
        }
        return sanitized;
    }

    public Path instanceDirectory() {
        return LauncherDirs.INSTANCES.resolve(sanitizeName(name));
    }

    public Path settingsFile() {
        return instanceDirectory().resolve("instance.json");
    }

    public Path gameDirectory() {
        if (gameFolder == null || gameFolder.isBlank()) {
            return instanceDirectory();
        }

        Path resolved = Path.of(gameFolder);
        return resolved.isAbsolute() ? resolved : instanceDirectory().resolve(resolved);
    }

    public void save() throws IOException {
        Path directory = instanceDirectory();
        Files.createDirectories(directory);
        Files.writeString(settingsFile(), GSON.toJson(this, Instance.class), StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return name;
    }
}
