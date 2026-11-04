package io.github.sebminecrafter.sebbylauncher;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LauncherPreferencesTest {
    @Test
    void olderSettingsWithoutJavaExecutableRemainCompatible() {
        LauncherPreferences preferences = new Gson().fromJson(
                "{\"lookAndFeel\":\"System\",\"accentColor\":\"#4F6D8A\"}",
                LauncherPreferences.class);

        assertNull(preferences.javaExecutablePath());
    }

    @Test
    void twoArgumentConstructorLeavesJavaExecutableUnselected() {
        LauncherPreferences preferences = new LauncherPreferences("System", "#4F6D8A");

        assertNull(preferences.javaExecutablePath());
    }

    @Test
    void customJavaExecutableCanBeStoredInPreferences() {
        LauncherPreferences preferences = new LauncherPreferences("System", "#4F6D8A", "C:\\Java\\bin\\java.exe");

        assertEquals("C:\\Java\\bin\\java.exe", preferences.javaExecutablePath());
    }
}
