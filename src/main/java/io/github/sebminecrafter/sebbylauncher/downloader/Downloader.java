package io.github.sebminecrafter.sebbylauncher.downloader;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import io.github.sebminecrafter.sebbylauncher.LauncherDirs;
import io.github.sebminecrafter.sebbylogger.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@SuppressWarnings("unused")
public class Downloader {
    private static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final String JAVA_RUNTIME_MANIFEST_URL = "https://piston-meta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
    private static final Logger logger = new Logger("SebbyLauncher/Downloader");
    private static final String RESOURCES_URL = "https://resources.download.minecraft.net/";
    private static final String QUILT_LOADER_METADATA_URL =
            "https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-loader/maven-metadata.xml";
    private static final Pattern QUILT_LATEST_VERSION_PATTERN =
            Pattern.compile("<latest>\\s*([^<]+?)\\s*</latest>");
    private static final int ASSET_THREADS = 16;
    private static final String JAVA_25_ASM_VERSION = "9.11.1";
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

    public VersionMeta getVersionMeta(String version, String loader) throws IOException {
        return getVersionMeta(version, loader, null);
    }

    public VersionMeta getVersionMeta(String version, String loader, String loaderVersion) throws IOException {
        VersionMeta base = getVersionMeta(version);
        if (base == null || loader == null || loader.isBlank()) return base;
        String normalized = normalizeLoader(loader);
        return switch (normalized) {
            case "fabric" -> applyFabricLoader(base, loaderVersion);
            case "quilt" -> applyQuiltLoader(base, loaderVersion);
            default -> base;
        };
    }

    public List<String> listLoaderVersions(String gameVersion, String loader) throws IOException {
        String normalized = normalizeLoader(loader);
        if (normalized.equals("vanilla")) return List.of();
        if (gameVersion == null || gameVersion.isBlank() || gameVersion.equalsIgnoreCase("latest")) {
            gameVersion = getLatestVersion(false);
            if (gameVersion == null) {
                throw new IOException("Could not resolve the latest Minecraft release.");
            }
        }

        String metadataUrl = switch (normalized) {
            case "fabric" -> "https://meta.fabricmc.net/v2";
            case "quilt" -> "https://meta.quiltmc.org/v3";
            default -> throw new IOException("Unsupported mod loader: " + loader);
        };
        JsonArray versions = readJsonArrayFromUrl(
                metadataUrl + "/versions/loader/" + gameVersion);
        if (versions == null) {
            throw new IOException("Could not retrieve " + loader + " loader versions for " + gameVersion);
        }

        List<String> result = new ArrayList<>();
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) continue;
            JsonObject info = element.getAsJsonObject().getAsJsonObject("loader");
            if (info != null && info.has("version") && !info.get("version").isJsonNull()) {
                result.add(info.get("version").getAsString());
            }
        }
        if (normalized.equals("quilt")) {
            String latestVersion = getLatestQuiltLoaderVersion();
            result.remove(latestVersion);
            result.addFirst(latestVersion);
        }
        return result;
    }

    public Path downloadJavaRuntime(VersionMeta.JavaVersion javaVersion) throws IOException {
        if (javaVersion == null || !"jre-legacy".equals(javaVersion.component())) {
            throw new IOException("Unsupported bundled Java runtime: "
                    + (javaVersion == null ? "unknown" : javaVersion.component()));
        }

        JsonObject runtimeList = readJsonObjectFromUrl(JAVA_RUNTIME_MANIFEST_URL);
        if (runtimeList == null || !runtimeList.isJsonObject()) {
            throw new IOException("Failed to read Java runtime manifest");
        }
        JsonElement platformEntry = runtimeList.get(runtimePlatform());
        if (platformEntry == null || !platformEntry.isJsonObject()) {
            throw new IOException("No Mojang Java runtime for platform " + runtimePlatform());
        }
        JsonElement candidatesEntry = platformEntry.getAsJsonObject().get(javaVersion.component());
        if (candidatesEntry == null || !candidatesEntry.isJsonArray() || candidatesEntry.getAsJsonArray().isEmpty()) {
            throw new IOException("No Mojang runtime candidate for " + javaVersion.component());
        }

        JsonObject candidate = candidatesEntry.getAsJsonArray().get(0).getAsJsonObject();
        String runtimeVersion = candidate.getAsJsonObject("version").get("name").getAsString();
        String manifestUrl = candidate.getAsJsonObject("manifest").get("url").getAsString();
        Path installDir = LauncherDirs.RUNTIMES.resolve(javaVersion.component()).resolve(runtimeVersion);
        Path executable = installDir.resolve("bin").resolve(VersionMeta.Platform.os().equals("windows") ? "java.exe" : "java");
        Path completeMarker = installDir.resolve(".installed");
        if (Files.isRegularFile(executable) && Files.isRegularFile(completeMarker)) return executable;

        JsonObject runtimeManifest = readJsonObjectFromUrl(manifestUrl);
        if (runtimeManifest == null || !runtimeManifest.isJsonObject()) {
            throw new IOException("Failed to read runtime manifest");
        }
        JsonObject files = runtimeManifest.getAsJsonObject("files");
        for (Map.Entry<String, JsonElement> entry : files.entrySet()) {
            JsonObject file = entry.getValue().getAsJsonObject();
            Path relative = Path.of(entry.getKey()).normalize();
            if (relative.isAbsolute() || relative.startsWith("..")) {
                throw new IOException("Invalid path in Mojang Java runtime manifest: " + entry.getKey());
            }
            Path destination = installDir.resolve(relative);
            String type = file.get("type").getAsString();
            if (type.equals("directory")) {
                Files.createDirectories(destination);
            } else if (type.equals("file")) {
                JsonObject downloads = file.getAsJsonObject("downloads");
                JsonObject raw = downloads == null ? null : downloads.getAsJsonObject("raw");
                if (raw == null) {
                    throw new IOException("Mojang runtime file has no raw download: " + entry.getKey());
                }
                downloadVerified(raw.get("url").getAsString(), destination, raw.get("sha1").getAsString());
                if (file.has("executable") && file.get("executable").getAsBoolean()
                        && !VersionMeta.Platform.os().equals("windows") && !destination.toFile().setExecutable(true)) {
                    throw new IOException("Could not mark Java runtime file executable: " + destination);
                }
            } else {
                throw new IOException("Unsupported Mojang Java runtime entry type: " + type);
            }
        }

        if (!Files.isRegularFile(executable)) {
            throw new IOException("Mojang Java runtime is missing its executable: " + executable);
        }
        Files.writeString(completeMarker, runtimeVersion);
        logger.info("Mojang Java runtime ready: " + executable);
        return executable;
    }

    private static String runtimePlatform() throws IOException {
        String os = VersionMeta.Platform.os();
        String arch = VersionMeta.Platform.arch();
        if (os.equals("windows")) {
            return switch (arch) {
                case "x86" -> "windows-x86";
                case "arm64" -> "windows-arm64";
                default -> "windows-x64";
            };
        }
        if (os.equals("osx")) return arch.equals("arm64") ? "mac-os-arm64" : "mac-os";
        if (os.equals("linux") && arch.equals("x86")) return "linux-i386";
        if (os.equals("linux") && arch.equals("x86_64")) return "linux";
        throw new IOException("No Mojang Java runtime mapping for " + os + " " + arch);
    }

    private static String normalizeLoader(String loader) {
        if (loader == null || loader.isBlank()) return "vanilla";
        String normalized = loader.trim();
        if (normalized.equalsIgnoreCase("vanilla") || normalized.equalsIgnoreCase("default")) return "vanilla";
        if (normalized.equalsIgnoreCase("fabric") || normalized.equalsIgnoreCase("fabric-loader")) return "fabric";
        if (normalized.equalsIgnoreCase("quilt") || normalized.equalsIgnoreCase("quilt-loader")) return "quilt";
        return normalized.toLowerCase(Locale.ROOT);
    }

    private VersionMeta applyFabricLoader(VersionMeta base, String loaderVersion) throws IOException {
        return applyLoaderProfile(base, "Fabric", "https://meta.fabricmc.net/v2", loaderVersion);
    }

    private VersionMeta applyQuiltLoader(VersionMeta base, String loaderVersion) throws IOException {
        return applyLoaderProfile(base, "Quilt", "https://meta.quiltmc.org/v3", loaderVersion);
    }

    private VersionMeta applyLoaderProfile(
            VersionMeta base, String loaderName, String metadataUrl, String requestedLoaderVersion)
            throws IOException {
        String version = base.id();
        boolean useLatest = requestedLoaderVersion == null || requestedLoaderVersion.isBlank()
                || "latest".equalsIgnoreCase(requestedLoaderVersion.trim());
        String loaderVersion;
        if (loaderName.equals("Quilt")) {
            loaderVersion = useLatest ? getLatestQuiltLoaderVersion() : requestedLoaderVersion.trim();
        } else {
            JsonArray versions = readJsonArrayFromUrl(metadataUrl + "/versions/loader/" + version);
            if (versions == null || versions.isEmpty()) {
                if (!useLatest) {
                    throw new IOException("No " + loaderName + " loader is available for version " + version);
                }
                logger.error("No " + loaderName + " loader is available for version " + version);
                return base;
            }

            JsonObject selected = null;
            if (!useLatest) {
                for (JsonElement element : versions) {
                    if (!element.isJsonObject()) continue;
                    JsonObject loader = element.getAsJsonObject().getAsJsonObject("loader");
                    if (loader != null && loader.has("version")
                            && requestedLoaderVersion.equals(loader.get("version").getAsString())) {
                        selected = element.getAsJsonObject();
                        break;
                    }
                }
                if (selected == null) {
                    throw new IOException(loaderName + " loader " + requestedLoaderVersion
                            + " is unavailable for Minecraft " + version);
                }
            } else {
                selected = versions.get(0).getAsJsonObject();
            }
            JsonObject loaderInfo = selected.has("loader") ? selected.getAsJsonObject("loader") : null;
            if (loaderInfo == null || loaderInfo.get("version") == null) {
                logger.error(loaderName + " loader metadata is incomplete for version " + version);
                return base;
            }
            loaderVersion = loaderInfo.get("version").getAsString();
        }

        JsonObject profile = readJsonObjectFromUrl(
                metadataUrl + "/versions/loader/" + version + "/" + loaderVersion + "/profile/json");
        if (profile == null) {
            logger.error("Could not fetch " + loaderName + " profile for " + version + " loader " + loaderVersion);
            return base;
        }

        int requiredJava = base.javaVersion() == null ? 0 : base.javaVersion().majorVersion();
        boolean quiltNeedsNewerAsm = loaderName.equals("Quilt") && requiredJava >= 25;
        List<VersionMeta.Library> mergedLibraries = new ArrayList<>(base.libraries() == null ? List.of() : base.libraries());
        JsonArray loaderLibraries = profile.has("libraries") ? profile.getAsJsonArray("libraries") : new JsonArray();
        if (loaderLibraries != null && !loaderLibraries.isEmpty()) {
            for (JsonElement element : loaderLibraries) {
                if (element == null || element.isJsonNull() || !element.isJsonObject()) continue;
                VersionMeta.Library lib = parseLoaderLibrary(element.getAsJsonObject());
                if (lib == null || lib.name() == null) continue;
                if (quiltNeedsNewerAsm) lib = upgradeAsmForJavaVersion(lib, requiredJava);
                mergeLoaderLibrary(mergedLibraries, lib, quiltNeedsNewerAsm);
            }
        }

        VersionMeta.Arguments arguments = base.arguments();
        if (profile.has("arguments") && !profile.get("arguments").isJsonNull()) {
            JsonObject argumentObject = profile.getAsJsonObject("arguments");
            VersionMeta.Arguments loaderArguments = new VersionMeta.Arguments(
                    parseArgumentList(argumentObject.get("game")),
                    parseArgumentList(argumentObject.get("jvm")));
            arguments = mergeArguments(arguments, loaderArguments);
        }

        String mainClass = profile.has("mainClass") ? profile.get("mainClass").getAsString() : base.mainClass();
        String type = profile.has("type") ? profile.get("type").getAsString() : base.type();
        return new VersionMeta(base.id(), type, mainClass, base.assets(), base.assetIndex(), base.javaVersion(),
                arguments, base.minecraftArguments(), base.downloads(), mergedLibraries);
    }

    private String getLatestQuiltLoaderVersion() throws IOException {
        return parseLatestQuiltLoaderVersion(readTextFromUrl(QUILT_LOADER_METADATA_URL));
    }

    static String parseLatestQuiltLoaderVersion(String metadata) throws IOException {
        Matcher latest = QUILT_LATEST_VERSION_PATTERN.matcher(metadata);
        if (!latest.find() || latest.group(1).isBlank()) {
            throw new IOException("Could not find the latest Quilt loader version in Maven metadata.");
        }
        return latest.group(1).trim();
    }

    private static VersionMeta.Arguments mergeArguments(VersionMeta.Arguments base, VersionMeta.Arguments loader) {
        List<JsonElement> game = new ArrayList<>();
        List<JsonElement> jvm = new ArrayList<>();
        if (base != null) {
            if (base.game() != null) game.addAll(base.game());
            if (base.jvm() != null) jvm.addAll(base.jvm());
        }
        if (loader != null) {
            if (loader.game() != null) game.addAll(loader.game());
            if (loader.jvm() != null) jvm.addAll(loader.jvm());
        }
        return new VersionMeta.Arguments(game, jvm);
    }

    private VersionMeta.Library parseLoaderLibrary(JsonObject obj) {
        JsonElement nameElement = obj.get("name");
        if (nameElement == null || nameElement.isJsonNull()) return null;
        String name = nameElement.getAsString();
        String repoUrl = obj.has("url") && !obj.get("url").isJsonNull() ? obj.get("url").getAsString() : null;
        String sha1 = obj.has("sha1") && !obj.get("sha1").isJsonNull() ? obj.get("sha1").getAsString() : null;
        long size = obj.has("size") && !obj.get("size").isJsonNull() ? obj.get("size").getAsLong() : 0L;

        String relativePath = relativeArtifactPath(name);
        String artifactUrl = relativePath == null ? mavenLibraryUrl(name) : joinUrl(repoUrl != null ? repoUrl : mavenBaseUrl(name), relativePath);
        VersionMeta.Artifact artifact = new VersionMeta.Artifact(relativePath, sha1, size, artifactUrl);

        List<VersionMeta.Rule> rules = null;
        if (obj.has("rules") && !obj.get("rules").isJsonNull()) {
            rules = GSON.fromJson(obj.get("rules"), new TypeToken<List<VersionMeta.Rule>>() {}.getType());
        }

        Map<String, String> natives = null;
        if (obj.has("natives") && !obj.get("natives").isJsonNull()) {
            natives = GSON.fromJson(obj.get("natives"), new TypeToken<Map<String, String>>() {}.getType());
        }

        VersionMeta.Extract extract = null;
        if (obj.has("extract") && !obj.get("extract").isJsonNull()) {
            extract = GSON.fromJson(obj.get("extract"), VersionMeta.Extract.class);
        }

        return new VersionMeta.Library(name, new VersionMeta.LibDownloads(artifact, Map.of()), natives, extract, rules);
    }

    static VersionMeta.Library upgradeAsmForJavaVersion(VersionMeta.Library library, int javaMajor) {
        if (javaMajor < 25 || !isAsmLibrary(library.name()) || supportsJava25Asm(library.name())) return library;

        String[] coordinates = library.name().split(":");
        String upgradedName = coordinates[0] + ":" + coordinates[1] + ":" + JAVA_25_ASM_VERSION;
        VersionMeta.Artifact artifact = new VersionMeta.Artifact(
                relativeArtifactPath(upgradedName), null, 0, mavenLibraryUrl(upgradedName));
        VersionMeta.LibDownloads downloads = new VersionMeta.LibDownloads(
                artifact, library.downloads() == null ? Map.of() : library.downloads().classifiers());
        return new VersionMeta.Library(
                upgradedName, downloads, library.natives(), library.extract(), library.rules());
    }

    static void mergeLoaderLibrary(
            List<VersionMeta.Library> libraries, VersionMeta.Library candidate, boolean replaceOldAsm) {
        for (int i = 0; i < libraries.size(); i++) {
            VersionMeta.Library existing = libraries.get(i);
            if (existing == null) continue;
            if (candidate.name().equals(existing.name())) return;
            if (replaceOldAsm && isAsmLibrary(candidate.name())
                    && sameMavenModule(existing.name(), candidate.name())) {
                if (!supportsJava25Asm(existing.name())) libraries.set(i, candidate);
                return;
            }
        }
        libraries.add(candidate);
    }

    private static boolean isAsmLibrary(String name) {
        return name != null && name.startsWith("org.ow2.asm:");
    }

    private static boolean supportsJava25Asm(String name) {
        if (!isAsmLibrary(name)) return false;
        String[] coordinates = name.split(":");
        if (coordinates.length < 3) return false;
        String[] version = coordinates[2].split("\\.");
        if (version.length < 2) return false;
        try {
            int major = Integer.parseInt(version[0]);
            int minor = Integer.parseInt(version[1]);
            return major > 9 || (major == 9 && minor >= 8);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean sameMavenModule(String first, String second) {
        if (first == null || second == null) return false;
        String[] firstCoordinates = first.split(":");
        String[] secondCoordinates = second.split(":");
        return firstCoordinates.length >= 3 && secondCoordinates.length >= 3
                && firstCoordinates[0].equals(secondCoordinates[0])
                && firstCoordinates[1].equals(secondCoordinates[1]);
    }

    private List<JsonElement> parseArgumentList(JsonElement element) {
        if (element == null || element.isJsonNull()) return List.of();
        if (element.isJsonArray()) {
            return GSON.fromJson(element, new TypeToken<List<JsonElement>>() {}.getType());
        }
        return List.of(element);
    }

    private JsonArray readJsonArrayFromUrl(String urlStr) throws IOException {
        JsonElement element = readJsonElementFromUrl(urlStr);
        return element == null || element.isJsonNull() ? null : element.getAsJsonArray();
    }

    private JsonObject readJsonObjectFromUrl(String urlStr) throws IOException {
        JsonElement element = readJsonElementFromUrl(urlStr);
        return element == null || element.isJsonNull() ? null : element.getAsJsonObject();
    }

    private String readTextFromUrl(String urlStr) throws IOException {
        HttpURLConnection conn = open(urlStr);
        conn.setRequestProperty("User-Agent", "SebbyLauncher");
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private JsonElement readJsonElementFromUrl(String urlStr) throws IOException {
        HttpURLConnection conn = open(urlStr);
        conn.setRequestProperty("User-Agent", "SebbyLauncher");
        try (InputStream in = conn.getInputStream(); Reader reader = new InputStreamReader(in)) {
            return JsonParser.parseReader(reader);
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
     */
    public void downloadLibraries(VersionMeta meta) throws IOException {
        downloadLibraries(meta, null);
    }

    public List<Path> downloadLibraries(VersionMeta meta, Path nativesDir) throws IOException {
        List<Path> classpath = new ArrayList<>();

        for (VersionMeta.Library lib : meta.libraries()) {
            if (!lib.appliesHere()) continue;

            if (lib.downloads() != null && lib.downloads().artifact() != null) {
                VersionMeta.Artifact artifact = lib.downloads().artifact();
                Path dest = LauncherDirs.LIBRARIES.resolve(artifact.path());
                logger.debug("Library: " + lib.name());

                downloadVerified(artifact.url(), dest, artifact.sha1());
                classpath.add(dest);
            } else if (lib.downloads() == null && lib.name() != null) {
                Path dest = mavenLibraryPath(lib.name());
                if (dest != null) {
                    logger.debug("Maven library: " + lib.name());
                    String url = mavenLibraryUrl(lib.name());
                    downloadVerified(url, dest, null);
                    classpath.add(dest);
                }
            }

            VersionMeta.DownloadInfo nativeInfo = lib.nativeDownload();
            if (nativeInfo != null && nativesDir != null) {
                String nativePath = lib.nativeArtifactPath();
                if (nativePath != null) {
                    Path dest = Path.of(nativePath);
                    Files.createDirectories(dest.getParent());
                    downloadVerified(nativeInfo.url(), dest, nativeInfo.sha1());
                    extractNatives(dest, nativesDir, lib.extract());
                }
            }
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
        if (missing.isEmpty()) {
            materializeLegacyAssets(index, ref.id());
            return;
        }

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
        materializeLegacyAssets(index, ref.id());
    }

    private void materializeLegacyAssets(AssetIndex index, String indexId) throws IOException {
        if (!index.virtual() && !index.mapToResources()) return;

        Path legacyRoot = index.mapToResources()
                ? LauncherDirs.ASSETS.resolve("resources")
                : LauncherDirs.ASSETS.resolve("virtual").resolve(indexId);
        for (Map.Entry<String, AssetIndex.AssetObject> entry : index.objects().entrySet()) {
            Path relative = Path.of(entry.getKey()).normalize();
            if (relative.isAbsolute() || relative.startsWith("..")) {
                throw new IOException("Invalid path in legacy asset index: " + entry.getKey());
            }
            Path source = assetPath(entry.getValue().hash());
            Path destination = legacyRoot.resolve(relative);
            Files.createDirectories(destination.getParent());
            if (!Files.isRegularFile(destination) || Files.size(destination) != entry.getValue().size()) {
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
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

    private void extractNatives(Path nativeJar, Path nativesDir, VersionMeta.Extract extract) throws IOException {
        if (nativeJar == null || !Files.isRegularFile(nativeJar)) return;
        List<String> excludes = extract == null || extract.exclude() == null ? List.of() : extract.exclude();
        Files.createDirectories(nativesDir);

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(nativeJar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String name = entry.getName();
                boolean excluded = excludes.stream().anyMatch(name::startsWith);
                if (excluded) continue;

                Path out = nativesDir.resolve(name);
                Files.createDirectories(out.getParent());
                try (OutputStream outStream = Files.newOutputStream(out)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        outStream.write(buffer, 0, read);
                    }
                }
            }
        }
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

    private static Path mavenLibraryPath(String name) {
        String relativePath = relativeArtifactPath(name);
        if (relativePath == null) return null;
        return LauncherDirs.LIBRARIES.resolve(relativePath);
    }

    private static String relativeArtifactPath(String name) {
        String[] parts = name.split(":");
        if (parts.length < 3) return null;
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".jar";
    }

    private static String joinUrl(String base, String relativePath) {
        if (base == null || base.isBlank()) return relativePath;
        String normalizedBase = base.endsWith("/") ? base : base + "/";
        return normalizedBase + relativePath;
    }

    private static String mavenBaseUrl(String group) {
        if (group == null || group.isBlank()) return "https://repo1.maven.org/maven2/";
        if (group.startsWith("net.fabricmc")) return "https://maven.fabricmc.net/";
        if (group.startsWith("org.quiltmc")) return "https://maven.quiltmc.org/repository/release/";
        return "https://repo1.maven.org/maven2/";
    }

    private static String mavenLibraryUrl(String name) {
        String[] parts = name.split(":");
        if (parts.length < 3) return null;
        String group = parts[0];
        String relativePath = relativeArtifactPath(name);
        if (relativePath == null) return null;
        return joinUrl(mavenBaseUrl(group), relativePath);
    }

    /**
     * Downloads to dest unless it already exists with the right hash.
     * Uses a temporary file so a failed download never leaves a broken dest.
     */
    private void downloadVerified(String urlStr, Path dest, String expectedSha1) throws IOException {
        if (Files.isRegularFile(dest)) {
            if (expectedSha1 == null || expectedSha1.isBlank()) return;
            if (expectedSha1.equalsIgnoreCase(sha1(dest))) return;
        }

        Files.createDirectories(dest.getParent());
        Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
        downloadFile(urlStr, tmp);

        if (expectedSha1 != null && !expectedSha1.isBlank() && !expectedSha1.equalsIgnoreCase(sha1(tmp))) {
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