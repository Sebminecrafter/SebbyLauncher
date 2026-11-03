package io.github.sebminecrafter.sebbylauncher;

import io.github.sebminecrafter.sebbylauncher.downloader.Downloader;
import io.github.sebminecrafter.sebbylauncher.downloader.VersionMeta;
import io.github.sebminecrafter.sebbylogger.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Launcher {
    private static final String DEFAULT_INSTANCE = "default";

    private final Logger logger;
    private final Logger gameLogger;
    private final Downloader downloader = new Downloader();

    /**
     * Create a new launcher instance (to launch the game)
     */
    public Launcher() {
        logger = new Logger("SebbyLauncher/Launch");
        gameLogger = new Logger("Minecraft");
    }

    /**
     * Launches the latest release. Blocks while downloading, so don't call it on the UI thread.
     *
     * @return true if the game was started
     */
    public boolean launchGame() {
        String version = downloader.getLatestVersion(false);
        if (version == null) {
            logger.error("Couldn't find the latest version.");
            return false;
        }
        return launchGame(version);
    }

    /**
     * Downloads (if needed) and starts the given version. Blocks until the game process
     * has started, not until it exits. Don't call it on the UI thread.
     *
     * @param version One of the strings returned by {@link Downloader#listVersions()}
     * @return true if the game was started
     */
    public boolean launchGame(String version) {
        logger.info("Starting game " + version + "...");
        try {
            VersionMeta meta = downloader.getVersionMeta(version);
            if (meta == null) return false;

            int requiredJava = meta.javaVersion() == null ? 8 : meta.javaVersion().majorVersion();
            if (Runtime.version().feature() < requiredJava) {
                logger.error(version + " needs Java " + requiredJava + ", but the launcher runs on Java " + Runtime.version().feature());
                return false;
            }

            Path clientJar = downloader.downloadClient(meta);
            List<Path> classpath = new ArrayList<>(downloader.downloadLibraries(meta));
            classpath.add(clientJar);
            downloader.downloadAssets(meta);

            Session session = Login.getSession();
            if (session == null) {
                logger.error("No Minecraft session available.");
                return false;
            }

            Path gameDir = LauncherDirs.INSTANCES.resolve(DEFAULT_INSTANCE);
            Path nativesDir = LauncherDirs.VERSIONS.resolve(version).resolve("natives");
            Files.createDirectories(gameDir);
            Files.createDirectories(nativesDir);

            List<String> command = LaunchCommand.build(meta, session, gameDir, nativesDir, classpath);
            logger.debug("Command: " + LaunchCommand.redact(command));

            start(command, gameDir);
            return true;

        } catch (IOException | UnsupportedOperationException e) {
            logger.error("Launch failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Starts the game process and forwards its output to the log.
     */
    private void start(List<String> command, Path gameDir) throws IOException {
        Process process = new ProcessBuilder(command)
                .directory(gameDir.toFile())
                .redirectErrorStream(true)
                .start();

        Thread.ofVirtual().name("minecraft-output").start(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                reader.lines().forEach(gameLogger::info);
            } catch (IOException e) {
                logger.error("Lost game output: " + e.getMessage());
            }
        });
        process.onExit().thenAccept(p -> logger.info("Game exited with code " + p.exitValue()));
    }
}
