package io.github.sebminecrafter.SebbyLauncher.Downloader;

import com.google.gson.*;
import io.github.sebminecrafter.SebbyLauncher.LauncherDirs;
import io.github.sebminecrafter.sebbylogger.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class Downloader {
    private static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final Logger logger = new Logger("SebbyLauncher/Downloader");

    private JsonObject manifestCache;

    /**
     * Lists all available Minecraft version IDs.
     */
    public String[] listVersions() {
        try {
            JsonObject manifest = getManifest();
            JsonArray versions = manifest.getAsJsonArray("versions");

            List<String> result = new ArrayList<>();
            for (JsonElement e : versions) {
                result.add(e.getAsJsonObject().get("id").getAsString());
            }

            logger.info("Found " + result.size() + " versions");
            return result.toArray(new String[0]);

        } catch (Exception e) {
            logger.error("Failed to list versions: " + e.getMessage());
            return new String[0];
        }
    }

    /**
     * Downloads the client JAR for the given version ID.
     *
     * @param version One of the strings returned by listVersions()
     * @return true on success, false on failure/invalid version
     */
    public boolean downloadVersion(String version) {
        try {
            JsonObject manifest = getManifest();
            JsonArray versions = manifest.getAsJsonArray("versions");

            JsonObject versionEntry = null;
            for (JsonElement e : versions) {
                JsonObject obj = e.getAsJsonObject();
                if (obj.get("id").getAsString().equals(version)) {
                    versionEntry = obj;
                    break;
                }
            }

            if (versionEntry == null) {
                logger.error("Invalid Minecraft version: " + version);
                return false;
            }

            String versionJsonUrl = versionEntry.get("url").getAsString();
            logger.debug("Fetching version JSON: " + versionJsonUrl);

            JsonObject versionJson = readJsonFromUrl(versionJsonUrl);

            if (versionJson == null) {

                return false;
            }

            String clientUrl = versionJson
                    .getAsJsonObject("downloads")
                    .getAsJsonObject("client")
                    .get("url")
                    .getAsString();

            Path outputDir = LauncherDirs.VERSIONS.resolve(version);
            Files.createDirectories(outputDir);

            Path outputJar = outputDir.resolve(version + ".jar");
            logger.info("Downloading " + version + " client.jar");

            downloadFile(clientUrl, outputJar);

            logger.info("Download complete: " + outputJar.toAbsolutePath());
            return true;

        } catch (Exception e) {
            logger.error("Download failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns the latest Minecraft version.
     *
     * @param snapshot if true, returns latest snapshot; otherwise latest release
     * @return version ID, or null on failure
     */
    public String getLatestVersion(boolean snapshot) {
        try {
            JsonObject manifest = getManifest();
            JsonObject latest = manifest.getAsJsonObject("latest");

            String version = snapshot
                    ? latest.get("snapshot").getAsString()
                    : latest.get("release").getAsString();

            logger.info(
                    "Latest Minecraft " + (snapshot ? "snapshot" : "release") + ": " + version
            );

            return version;
        } catch (Exception e) {
            logger.error("Failed to get latest version: " + e.getMessage());
            return null;
        }
    }

    private HttpURLConnection open(String urlStr) throws IOException {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URI(urlStr).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            return conn;
        } catch (URISyntaxException e) {
            throw new IOException("Bad URL: " + urlStr, e);
        }
    }

    private JsonObject getManifest() throws IOException {
        if (manifestCache == null) {
            logger.debug("Fetching version manifest");
            manifestCache = readJsonFromUrl(MANIFEST_URL);
        }
        return manifestCache;
    }

    private JsonObject readJsonFromUrl(String urlStr) throws IOException {
        HttpURLConnection conn = open(urlStr);

        try (InputStream in = conn.getInputStream();
             Reader reader = new InputStreamReader(in)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private void downloadFile(String urlStr, Path output) throws IOException {
        HttpURLConnection conn = open(urlStr);

        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}