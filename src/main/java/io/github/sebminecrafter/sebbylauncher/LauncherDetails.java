package io.github.sebminecrafter.sebbylauncher;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class LauncherDetails {
    public static final String NAME = "SebbyLauncher";
    public static final String VERSION = getVersion();
    public static final String BUILD_TIME = getBuildTime();

    /**
     * Reads the launcher version, or "dev" when running from an IDE (unfiltered
     * resources).
     */
    public static String getVersion() {
        try (InputStream in = Main.class.getResourceAsStream("/assets/text/version.properties")) {
            if (in == null)
                return "dev";
            Properties props = new Properties();
            props.load(in);
            String version = props.getProperty("sebbylauncher_version", "dev");
            return version.startsWith("${") ? "dev" : version;
        } catch (IOException e) {
            return "dev";
        }
    }

    /**
     * Reads the launcher version, or "dev" when running from an IDE (unfiltered
     * resources).
     */
    public static String getBuildTime() {
        try (InputStream in = Main.class.getResourceAsStream("/assets/text/version.properties")) {
            if (in == null)
                return "dev";
            Properties props = new Properties();
            props.load(in);
            String version = props.getProperty("built_at", "dev");
            return version.startsWith("${") ? "dev" : version;
        } catch (IOException e) {
            return "dev";
        }
    }
}
