package io.github.sebminecrafter.sebbylauncher.window;

import io.github.sebminecrafter.sebbylauncher.LauncherPreferences;

import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import javax.swing.UIManager;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsPageTest {
    @Test
    void themeDisplayNameAcceptsStoredClassNamesAndDisplayNames() {
        UIManager.LookAndFeelInfo theme = Arrays.stream(UIManager.getInstalledLookAndFeels())
                .findFirst()
                .orElseThrow();

        assertEquals(theme.getName(), SettingsPage.themeDisplayName(theme.getClassName()));
        assertEquals(theme.getName(), SettingsPage.themeDisplayName(theme.getName()));
    }

    @Test
    void themeDisplayNameUsesSystemForSystemOrUnavailableThemes() {
        assertEquals("System", SettingsPage.themeDisplayName("System"));
        assertEquals("System", SettingsPage.themeDisplayName("unavailable.look.and.feel"));
        assertEquals("System", SettingsPage.themeDisplayName(null));
    }

    @Test
    void refreshThemeSelectionReflectsTheStoredPreference() {
        UIManager.LookAndFeelInfo theme = Arrays.stream(UIManager.getInstalledLookAndFeels())
                .findFirst()
                .orElseThrow();
        JComboBox<String> previousSelector = Window.themeSelector;
        LauncherPreferences previousPreferences = Window.preferences;
        try {
            Window.themeSelector = new JComboBox<>(new String[] {"System", theme.getName()});
            Window.preferences = new LauncherPreferences(theme.getClassName(), "#4F6D8A");

            SettingsPage.refreshThemeSelection();

            assertEquals(theme.getName(), Window.themeSelector.getSelectedItem());
        } finally {
            Window.themeSelector = previousSelector;
            Window.preferences = previousPreferences;
        }
    }
}
