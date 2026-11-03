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

@SuppressWarnings("unused")
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
     * Launches the latest release. Blocks while downloading, so don't call it on
     * the UI thread.
     *
     * @return true if the game was started
     */
    public boolean launchGame() {
        List<Instance> instances = Instance.loadAll();
        Instance selected = instances.isEmpty() ? Instance.defaultInstance() : instances.getFirst();
        return launchGame(selected);
    }

    public boolean launchGame(Instance instance) {
        if (instance == null) {
            instance = Instance.defaultInstance();
        }

        String requestedVersion = instance.version();
        String version = requestedVersion == null || requestedVersion.isBlank()
                || requestedVersion.equalsIgnoreCase("latest")
                        ? downloader.getLatestVersion(false)
                        : requestedVersion;
        if (version == null) {
            logger.error("Couldn't find the latest version.");
            return false;
        }
        return launchGame(version, instance);
    }

    /**
     * Downloads (if needed) and starts the given version. Blocks until the game
     * process
     * has started, not until it exits. Don't call it on the UI thread.
     *
     * @param version One of the strings returned by
     *                {@link Downloader#listVersions()}
     * @return true if the game was started
     */
    public boolean launchGame(String version) {
        return launchGame(version, null);
    }

    private boolean launchGame(String version, Instance instance) {
        logger.info("Starting game " + version + "...");
        try {
            String loader = instance == null ? "Vanilla" : instance.loader();
            VersionMeta meta = downloader.getVersionMeta(
                    version, loader, instance == null ? null : instance.loaderVersion());
            if (meta == null)
                return false;

            int requiredJava = meta.javaVersion() == null ? 8 : meta.javaVersion().majorVersion();
            boolean legacyRuntime = meta.javaVersion() != null
                    && "jre-legacy".equals(meta.javaVersion().component());
            if (!legacyRuntime && Runtime.version().feature() < requiredJava) {
                logger.error(version + " needs Java " + requiredJava + ", but the launcher runs on Java "
                        + Runtime.version().feature());
                return false;
            }
            Path javaExecutable = legacyRuntime
                    ? downloader.downloadJavaRuntime(meta.javaVersion())
                    : Path.of(System.getProperty("java.home"), "bin", "java");

            Path clientJar = downloader.downloadClient(meta);
            Path nativesDir = LauncherDirs.VERSIONS.resolve(version).resolve("natives");
            Files.createDirectories(nativesDir);
            List<Path> classpath = new ArrayList<>(downloader.downloadLibraries(meta, nativesDir));
            classpath.add(clientJar);
            downloader.downloadAssets(meta);

            Session session = Login.getSession();
            if (session == null) {
                logger.error("No Minecraft session available.");
                return false;
            }

            Path gameDir = instance == null ? LauncherDirs.INSTANCES.resolve(DEFAULT_INSTANCE)
                    : instance.gameDirectory();
            Files.createDirectories(gameDir);

            String jvmArgs = instance == null ? ""
                    : String.join(" ", instance.jvmArgs() == null ? List.of() : instance.jvmArgs());
            List<String> command = LaunchCommand.build(meta, session, gameDir, nativesDir, classpath,
                    instance == null ? 2048 : instance.memoryMb(), jvmArgs, javaExecutable);
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
