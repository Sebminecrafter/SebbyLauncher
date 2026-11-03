package io.github.sebminecrafter.sebbylauncher.downloader;

import com.google.gson.*;
import io.github.sebminecrafter.sebbylauncher.LauncherDirs;
import io.github.sebminecrafter.sebbylogger.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class Downloader {
    private static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final Logger logger = new Logger("SebbyLauncher/Downloader");
    private static final String RESOURCES_URL = "https://resources.download.minecraft.net/";
    private static final int ASSET_THREADS = 16;
    private static final Gson GSON = new Gson();

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
     * Downloads the client JAR, libraries, and assets for the given version ID.
     *
     * @param version One of the strings returned by listVersions()
     * @return true on success, false on failure/invalid version
     */
    public boolean downloadVersion(String version) {
        try {
            VersionMeta meta = getVersionMeta(version);
            if (meta == null) return false;

            Path clientJar = downloadClient(meta);
            downloadLibraries(meta);
            downloadAssets(meta);

            logger.info("Download complete: " + clientJar.toAbsolutePath());
            return true;

        } catch (Exception e) {
            logger.error("Download failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Downloads the client JAR for the given version.
     *
     * @return path of the client JAR
     */
    public Path downloadClient(VersionMeta meta) throws IOException {
        VersionMeta.DownloadInfo client = meta.downloads().get("client");
        Path jar = LauncherDirs.VERSIONS.resolve(meta.id()).resolve(meta.id() + ".jar");
        logger.info("Downloading " + meta.id() + " client.jar");

        downloadVerified(client.url(), jar, client.sha1());
        return jar;
    }

    /**
     * Loads the version JSON for the given ID, downloading it if it is not saved
     * yet.
     *
     * @param version One of the strings returned by listVersions()
     * @return the parsed version JSON, or null if the version is invalid
     */
    public VersionMeta getVersionMeta(String version) throws IOException {
        Path file = LauncherDirs.VERSIONS.resolve(version).resolve(version + ".json");

        if (!Files.isRegularFile(file)) {
            JsonObject entry = findVersionEntry(version);
            if (entry == null) {
                logger.error("Invalid Minecraft version: " + version);
                return null;
            }
            String url = entry.get("url").getAsString();
            logger.debug("Fetching version JSON: " + url);
            downloadVerified(url, file, versionMetaSha1(entry, url));
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, VersionMeta.class);
        }
    }

    private static String versionMetaSha1(JsonObject entry, String url) throws IOException {
        JsonElement sha1 = entry.get("sha1");
        if (sha1 != null && !sha1.isJsonNull()) return sha1.getAsString();

        String[] segments = URI.create(url).getPath().split("/");
        for (int i = 0; i < segments.length - 1; i++) {
            if (segments[i].equals("packages") && segments[i + 1].matches("[0-9a-fA-F]{40}")) {
                return segments[i + 1];
            }
        }
        throw new IOException("Version manifest entry has no SHA-1 checksum: " + url);
    }

    /**
     * Downloads every library the version needs on this platform.
     *
     * @return paths of the library JARs, in classpath order
     */
    public List<Path> downloadLibraries(VersionMeta meta) throws IOException {
        List<Path> classpath = new ArrayList<>();

        for (VersionMeta.Library lib : meta.libraries()) {
            if (!lib.appliesHere()) continue;
            if (lib.downloads() == null || lib.downloads().artifact() == null) continue;

            VersionMeta.Artifact artifact = lib.downloads().artifact();
            Path dest = LauncherDirs.LIBRARIES.resolve(artifact.path());
            logger.debug("Library: " + lib.name());

            downloadVerified(artifact.url(), dest, artifact.sha1());
            classpath.add(dest);
        }

        logger.info("Libraries ready: " + classpath.size());
        return classpath;
    }

    /**
     * Downloads the asset index and every asset it lists that is missing or the
     * wrong size.
     * Files are downloaded in parallel.
     */
    public void downloadAssets(VersionMeta meta) throws IOException {
        VersionMeta.AssetIndexRef ref = meta.assetIndex();
        if (ref == null) return;

        Path indexFile = LauncherDirs.ASSETS.resolve("indexes").resolve(ref.id() + ".json");
        downloadVerified(ref.url(), indexFile, ref.sha1());

        AssetIndex index;
        try (Reader reader = Files.newBufferedReader(indexFile)) {
            index = GSON.fromJson(reader, AssetIndex.class);
        }
        if (index.virtual() || index.mapToResources()) {
            logger.warn("Legacy asset layout is not supported yet: " + ref.id());
        }

        // several names can share one file, so dedupe by hash
        Map<String, AssetIndex.AssetObject> unique = new HashMap<>();
        for (AssetIndex.AssetObject obj : index.objects().values())
            unique.putIfAbsent(obj.hash(), obj);

        List<AssetIndex.AssetObject> missing = new ArrayList<>();
        for (AssetIndex.AssetObject obj : unique.values()) {
            Path file = assetPath(obj.hash());
            if (!Files.isRegularFile(file) || Files.size(file) != obj.size()) missing.add(obj);
        }

        logger.info("Assets: " + missing.size() + " of " + unique.size() + " to download");
        if (missing.isEmpty()) return;

        AtomicInteger done = new AtomicInteger();
        List<Future<Void>> tasks = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(ASSET_THREADS)) {
            for (AssetIndex.AssetObject obj : missing) {
                tasks.add(pool.submit(() -> {
                    String hash = obj.hash();
                    downloadVerified(RESOURCES_URL + hash.substring(0, 2) + "/" + hash, assetPath(hash), hash);
                    int n = done.incrementAndGet();
                    if (n % 250 == 0) logger.info("Assets: " + n + "/" + missing.size());
                    return null;
                }));
            }
        }

        int failed = 0;
        for (Future<Void> task : tasks) {
            try {
                task.get();
            } catch (ExecutionException e) {
                failed++;
                logger.error("Asset failed: " + e.getCause().getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while downloading assets", e);
            }
        }
        if (failed > 0) throw new IOException(failed + " assets failed to download");
        logger.info("Assets ready: " + unique.size());
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

            String version = snapshot ? latest.get("snapshot").getAsString() : latest.get("release").getAsString();

            logger.info("Latest Minecraft " + (snapshot ? "snapshot" : "release") + ": " + version);

            return version;
        } catch (Exception e) {
            logger.error("Failed to get latest version: " + e.getMessage());
            return null;
        }
    }

    private JsonObject findVersionEntry(String version) throws IOException {
        for (JsonElement e : getManifest().getAsJsonArray("versions")) {
            JsonObject obj = e.getAsJsonObject();
            if (obj.get("id").getAsString().equals(version)) return obj;
        }
        return null;
    }

    private static Path assetPath(String hash) {
        return LauncherDirs.ASSETS.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
    }

    private static String sha1(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Downloads to dest unless it already exists with the right hash.
     * Uses a temporary file so a failed download never leaves a broken dest.
     */
    private void downloadVerified(String urlStr, Path dest, String sha1) throws IOException {
        if (Files.isRegularFile(dest) && sha1.equalsIgnoreCase(sha1(dest))) return;

        Files.createDirectories(dest.getParent());
        Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
        downloadFile(urlStr, tmp);

        if (!sha1.equalsIgnoreCase(sha1(tmp))) {
            Files.deleteIfExists(tmp);
            throw new IOException("Checksum mismatch: " + urlStr);
        }
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
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

        try (InputStream in = conn.getInputStream(); Reader reader = new InputStreamReader(in)) {
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