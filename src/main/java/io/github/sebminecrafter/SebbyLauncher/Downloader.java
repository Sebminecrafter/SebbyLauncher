package io.github.sebminecrafter.SebbyLauncher;

import com.google.gson.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

import static io.github.sebminecrafter.SebbyLauncher.Main.logger;

public class Downloader {

    private static final String SOURCE = "DownloadMinecraft";
    private static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";

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

            logger.info("Found " + result.size() + " versions", SOURCE);
            return result.toArray(new String[0]);

        } catch (Exception e) {
            logger.error("Failed to list versions: " + e.getMessage(), SOURCE);
            return new String[0];
        }
    }

    /**
     * Downloads the client JAR for the given version ID.
     *
     * @param version One of the strings returned by listVersions()
     * @return true on success, false on failure or invalid version
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
                logger.error("Invalid Minecraft version: " + version, SOURCE);
                return false;
            }

            String versionJsonUrl = versionEntry.get("url").getAsString();
            logger.debug("Fetching version JSON: " + versionJsonUrl, SOURCE);

            JsonObject versionJson = readJsonFromUrl(versionJsonUrl);

            String clientUrl = versionJson
                    .getAsJsonObject("downloads")
                    .getAsJsonObject("client")
                    .get("url")
                    .getAsString();

            Path outputDir = Paths.get("minecraft", "versions", version);
            Files.createDirectories(outputDir);

            Path outputJar = outputDir.resolve(version + ".jar");
            logger.info("Downloading " + version + " client.jar", SOURCE);

            downloadFile(clientUrl, outputJar);

            logger.info("Download complete: " + outputJar.toAbsolutePath(), SOURCE);
            return true;

        } catch (Exception e) {
            logger.error("Download failed: " + e.getMessage(), SOURCE);
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
                    "Latest Minecraft " + (snapshot ? "snapshot" : "release") + ": " + version,
                    SOURCE
            );

            return version;
        } catch (Exception e) {
            logger.error("Failed to get latest version: " + e.getMessage(), SOURCE);
            return null;
        }
    }

    private JsonObject getManifest() throws IOException {
        if (manifestCache == null) {
            logger.debug("Fetching version manifest", SOURCE);
            manifestCache = readJsonFromUrl(MANIFEST_URL);
        }
        return manifestCache;
    }

    private JsonObject readJsonFromUrl(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        try (InputStream in = conn.getInputStream();
             Reader reader = new InputStreamReader(in)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private void downloadFile(String urlStr, Path output) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}