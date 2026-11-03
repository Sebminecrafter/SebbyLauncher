package io.github.sebminecrafter.sebbylauncher;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record LauncherPreferences(String lookAndFeel, String accentColor) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = LauncherDirs.ROOT.resolve("settings.json");

    public static LauncherPreferences defaults() {
        return new LauncherPreferences("System", "#4F6D8A");
    }

    public static LauncherPreferences load() throws IOException {
        if (!Files.isRegularFile(FILE)) {
            return defaults();
        }
        try (var reader = Files.newBufferedReader(FILE)) {
            LauncherPreferences preferences = GSON.fromJson(reader, LauncherPreferences.class);
            if (preferences == null || preferences.lookAndFeel() == null || preferences.lookAndFeel().isBlank()
                    || preferences.accentColor() == null
                    || !preferences.accentColor().matches("#[0-9A-Fa-f]{6}")) {
                throw new IOException("Settings file is missing required values.");
            }
            return preferences;
        } catch (RuntimeException e) {
            throw new IOException("Settings file is invalid.", e);
        }
    }

    public void save() throws IOException {
        if (lookAndFeel == null || lookAndFeel.isBlank() || accentColor == null
                || !accentColor.matches("#[0-9A-Fa-f]{6}")) {
            throw new IOException("Launcher settings contain invalid theme values.");
        }
        Files.createDirectories(FILE.getParent());
        Files.writeString(FILE, GSON.toJson(this));
    }

    public static Path file() {
        return FILE;
    }
}
