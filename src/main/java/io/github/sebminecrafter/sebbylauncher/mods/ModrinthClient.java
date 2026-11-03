package io.github.sebminecrafter.sebbylauncher.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.sebminecrafter.sebbylauncher.Instance;
import io.github.sebminecrafter.sebbylauncher.LauncherDetails;
import io.github.sebminecrafter.sebbylauncher.downloader.Downloader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ModrinthClient {
    private static final URI API_BASE = URI.create("https://api.modrinth.com/v2/");
    private static final String USER_AGENT = LauncherDetails.NAME + "/" + LauncherDetails.VERSION;
    private static final long MAX_API_RESPONSE_BYTES = 16L * 1024 * 1024;
    private static final long MAX_MOD_BYTES = 512L * 1024 * 1024;
    private static final String REGISTRY_FILE = ".sebbylauncher-mods.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type INSTALLED_LIST_TYPE = new TypeToken<List<InstalledMod>>() {}.getType();
    private final HttpClient http;
    private final URI apiBase;
    private volatile String latestMinecraftVersion;

    public ModrinthClient() {
        this(HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(15))
                        .build(),
                API_BASE);
    }

    ModrinthClient(HttpClient http, URI apiBase) {
        this.http = Objects.requireNonNull(http);
        String base = apiBase.toString();
        this.apiBase = URI.create(base.endsWith("/") ? base : base + "/");
    }

    public List<Project> search(String query, String gameVersion, String loader)
            throws IOException, InterruptedException {
        gameVersion = resolveMinecraftVersion(gameVersion);
        String loaderId = loaderId(loader);
        List<List<String>> facets = new ArrayList<>();
        facets.add(List.of("project_type:mod"));
        facets.add(List.of("versions:" + gameVersion));
        if (loaderId != null) facets.add(List.of("categories:" + loaderId));

        String url = "search?query=" + encode(query == null ? "" : query.trim())
                + "&limit=20&facets=" + encode(GSON.toJson(facets));
        JsonObject response = getJsonObject(apiBase.resolve(url));
        JsonArray hits = response.getAsJsonArray("hits");
        List<Project> projects = new ArrayList<>();
        for (JsonElement element : hits) {
            JsonObject hit = element.getAsJsonObject();
            projects.add(new Project(
                    requiredString(hit, "project_id"),
                    optionalString(hit, "slug"),
                    optionalString(hit, "title"),
                    optionalString(hit, "description"),
                    hit.has("downloads") && !hit.get("downloads").isJsonNull()
                            ? hit.get("downloads").getAsLong() : 0));
        }
        return projects;
    }

    public List<InstalledMod> listInstalled(Instance instance) throws IOException {
        Path modsDirectory = modsDirectory(instance);
        if (Files.isSymbolicLink(modsDirectory)) {
            throw new IOException("The mods folder must not be a symbolic link.");
        }
        Path registry = modsDirectory.resolve(REGISTRY_FILE);
        if (!Files.exists(registry, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (Files.isSymbolicLink(registry)) {
            throw new IOException("The installed-mod registry must not be a symbolic link.");
        }

        List<InstalledMod> installed;
        try (var reader = Files.newBufferedReader(registry, StandardCharsets.UTF_8)) {
            installed = GSON.fromJson(reader, INSTALLED_LIST_TYPE);
        } catch (RuntimeException e) {
            throw new IOException("The installed-mod registry is invalid.", e);
        }
        if (installed == null) return List.of();
        for (InstalledMod mod : installed) {
            validateProjectId(mod.projectId());
            validateFileName(mod.fileName());
            if (!Files.isRegularFile(modsDirectory.resolve(mod.fileName()), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Tracked mod file is missing: " + mod.fileName());
            }
        }
        return List.copyOf(installed);
    }

    public List<ModUpdate> checkForUpdates(Instance instance) throws IOException, InterruptedException {
        List<ModUpdate> updates = new ArrayList<>();
        String gameVersion = resolveMinecraftVersion(instance.version());
        for (InstalledMod installed : listInstalled(instance)) {
            JsonObject latest = latestCompatibleVersion(installed.projectId(), gameVersion, instance.loader());
            if (latest == null) continue;
            String versionId = requiredString(latest, "id");
            if (!versionId.equals(installed.versionId())) {
                updates.add(new ModUpdate(installed, versionId, optionalString(latest, "version_number")));
            }
        }
        return updates;
    }

    public InstalledMod install(Instance instance, Project project) throws IOException, InterruptedException {
        String gameVersion = resolveMinecraftVersion(instance.version());
        JsonObject version = latestCompatibleVersion(project.projectId(), gameVersion, instance.loader());
        if (version == null) {
            throw new IOException("No compatible version of " + project.title() + " was found.");
        }
        return installVersion(instance, project.projectId(), project.title(), version);
    }

    public void update(Instance instance, ModUpdate update) throws IOException, InterruptedException {
        JsonObject version = getVersion(update.latestVersionId());
        installVersion(instance, update.installed().projectId(), update.installed().title(), version);
    }

    private InstalledMod installVersion(Instance instance, String projectId, String title, JsonObject version)
            throws IOException, InterruptedException {
        validateProjectId(projectId);
        JsonArray files = version.getAsJsonArray("files");
        if (files == null || files.isEmpty()) throw new IOException("Modrinth returned a version without files.");

        JsonObject selectedFile = null;
        for (JsonElement element : files) {
            JsonObject file = element.getAsJsonObject();
            if (file.has("primary") && file.get("primary").getAsBoolean()) {
                selectedFile = file;
                break;
            }
            if (selectedFile == null) selectedFile = file;
        }
        if (selectedFile == null) throw new IOException("Modrinth returned a version without a downloadable file.");

        String fileName = requiredString(selectedFile, "filename");
        validateFileName(fileName);
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IOException("Refusing to install a mod file that is not a JAR: " + fileName);
        }
        String downloadUrl = requiredString(selectedFile, "url");
        URI downloadUri;
        try {
            downloadUri = URI.create(downloadUrl);
        } catch (IllegalArgumentException e) {
            throw new IOException("Modrinth returned an invalid download URL.", e);
        }
        if (!"https".equalsIgnoreCase(downloadUri.getScheme()) || downloadUri.getHost() == null
                || downloadUri.getUserInfo() != null) {
            throw new IOException("Refusing an unsafe mod download URL.");
        }
        JsonObject hashes = selectedFile.has("hashes") && selectedFile.get("hashes").isJsonObject()
                ? selectedFile.getAsJsonObject("hashes") : new JsonObject();
        if (!hashes.has("sha512") && !hashes.has("sha1")) {
            throw new IOException("Modrinth did not provide a checksum for " + fileName + ".");
        }

        Path modsDirectory = modsDirectory(instance);
        Files.createDirectories(modsDirectory);
        if (Files.isSymbolicLink(modsDirectory)) throw new IOException("The mods folder must not be a symbolic link.");
        List<InstalledMod> installed = new ArrayList<>(listInstalled(instance));
        InstalledMod previous = installed.stream()
                .filter(mod -> mod.projectId().equals(projectId))
                .findFirst().orElse(null);
        Path destination = chooseDestination(modsDirectory, fileName, projectId, previous);
        Path temporaryFile = Files.createTempFile(modsDirectory, ".sebbylauncher-", ".tmp");
        try {
            downloadFile(downloadUri, temporaryFile, hashes);
            moveReplacing(temporaryFile, destination);

            InstalledMod result = new InstalledMod(projectId, title, requiredString(version, "id"),
                    optionalString(version, "version_number"), destination.getFileName().toString());
            installed.removeIf(mod -> mod.projectId().equals(projectId));
            installed.add(result);
            writeRegistry(modsDirectory, installed);

            if (previous != null && !previous.fileName().equals(result.fileName())) {
                Files.deleteIfExists(modsDirectory.resolve(previous.fileName()));
            }
            return result;
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private JsonObject latestCompatibleVersion(String projectId, String gameVersion, String loader)
            throws IOException, InterruptedException {
        validateProjectId(projectId);
        requireText(gameVersion, "Minecraft version");
        String loaderId = loaderId(loader);
        String url = "project/" + projectId + "/version?game_versions=" + encode(GSON.toJson(List.of(gameVersion)));
        if (loaderId != null) url += "&loaders=" + encode(GSON.toJson(List.of(loaderId)));
        JsonArray versions = getJsonArray(apiBase.resolve(url));
        return versions.isEmpty() ? null : versions.get(0).getAsJsonObject();
    }

    private String resolveMinecraftVersion(String version) throws IOException {
        requireText(version, "Minecraft version");
        if (!"latest".equalsIgnoreCase(version.trim())) return version.trim();
        String cachedVersion = latestMinecraftVersion;
        if (cachedVersion == null) {
            synchronized (this) {
                cachedVersion = latestMinecraftVersion;
                if (cachedVersion == null) {
                    cachedVersion = new Downloader().getLatestVersion(false);
                    if (cachedVersion == null || cachedVersion.isBlank()) {
                        throw new IOException("Could not resolve the latest Minecraft release.");
                    }
                    latestMinecraftVersion = cachedVersion;
                }
            }
        }
        return cachedVersion;
    }

    private JsonObject getVersion(String versionId) throws IOException, InterruptedException {
        validateProjectId(versionId);
        return getJsonObject(apiBase.resolve("version/" + versionId));
    }

    private JsonObject getJsonObject(URI uri) throws IOException, InterruptedException {
        JsonElement element = JsonParser.parseString(new String(getBytes(uri),
                StandardCharsets.UTF_8));
        if (!element.isJsonObject()) throw new IOException("Modrinth returned an invalid JSON object.");
        return element.getAsJsonObject();
    }

    private JsonArray getJsonArray(URI uri) throws IOException, InterruptedException {
        JsonElement element = JsonParser.parseString(new String(getBytes(uri),
                StandardCharsets.UTF_8));
        if (!element.isJsonArray()) throw new IOException("Modrinth returned an invalid JSON array.");
        return element.getAsJsonArray();
    }

    private byte[] getBytes(URI uri) throws IOException, InterruptedException {
        HttpRequest request = request(uri).GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Modrinth returned HTTP " + response.statusCode() + " for " + uri.getPath());
            }
            byte[] data = body.readNBytes((int) ModrinthClient.MAX_API_RESPONSE_BYTES + 1);
            if (data.length > ModrinthClient.MAX_API_RESPONSE_BYTES) throw new IOException("Modrinth response exceeded the size limit.");
            return data;
        }
    }

    private void downloadFile(URI uri, Path destination, JsonObject hashes)
            throws IOException, InterruptedException {
        HttpRequest request = request(uri).GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Mod download returned HTTP " + response.statusCode() + ".");
            }
            if (response.headers().firstValueAsLong("Content-Length").orElse(0) > MAX_MOD_BYTES) {
                throw new IOException("Mod file exceeds the 512 MB download limit.");
            }
            String algorithm = hashes.has("sha512") ? "SHA-512" : "SHA-1";
            String expectedHash = hashes.get(algorithm.equals("SHA-512") ? "sha512" : "sha1").getAsString();
            MessageDigest digest = newDigest(algorithm);
            long total = 0;
            byte[] buffer = new byte[32 * 1024];
            try (OutputStream output = Files.newOutputStream(destination)) {
                int count;
                while ((count = body.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_MOD_BYTES) throw new IOException("Mod file exceeds the 512 MB download limit.");
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
            }
            if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expectedHash)) {
                throw new IOException("Downloaded mod failed its " + algorithm + " checksum.");
            }
        }
    }

    private HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json");
    }

    private static MessageDigest newDigest(String algorithm) throws IOException {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Checksum algorithm is unavailable: " + algorithm, e);
        }
    }

    private static Path modsDirectory(Instance instance) throws IOException {
        try {
            Path gameDirectory = instance.gameDirectory().toAbsolutePath().normalize();
            if (Files.isSymbolicLink(gameDirectory)) {
                throw new IOException("The instance game folder must not be a symbolic link.");
            }
            return gameDirectory.resolve("mods");
        } catch (RuntimeException e) {
            throw new IOException("The instance game folder is invalid.", e);
        }
    }

    private static Path chooseDestination(Path modsDirectory, String fileName, String projectId, InstalledMod previous)
            throws IOException {
        Path destination = modsDirectory.resolve(fileName);
        if (Files.isSymbolicLink(destination)) throw new IOException("Refusing to replace a symbolic link: " + fileName);
        if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS) || (previous != null
                && previous.fileName().equals(fileName))) return destination;

        String stem = fileName.substring(0, fileName.length() - 4);
        Path alternative = modsDirectory.resolve(stem + "-" + projectId + ".jar");
        if (Files.isSymbolicLink(alternative)) {
            throw new IOException("Refusing to replace a symbolic link: " + alternative.getFileName());
        }
        if (Files.exists(alternative, LinkOption.NOFOLLOW_LINKS)
                && (previous == null || !previous.fileName().equals(alternative.getFileName().toString()))) {
            throw new IOException("A mod file already exists at " + alternative.getFileName() + ".");
        }
        return alternative;
    }

    private static void writeRegistry(Path modsDirectory, List<InstalledMod> installed) throws IOException {
        Path registry = modsDirectory.resolve(REGISTRY_FILE);
        if (Files.isSymbolicLink(registry)) throw new IOException("The installed-mod registry must not be a symbolic link.");
        Path temporary = Files.createTempFile(modsDirectory, ".sebbylauncher-registry-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(installed), StandardCharsets.UTF_8);
            moveReplacing(temporary, registry);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String loaderId(String loader) throws IOException {
        if (loader == null || loader.isBlank() || loader.equalsIgnoreCase("vanilla")) return null;
        return switch (loader.trim().toLowerCase(Locale.ROOT)) {
            case "fabric" -> "fabric";
            case "quilt" -> "quilt";
            case "forge" -> "forge";
            case "neoforge", "neo forge" -> "neoforge";
            default -> throw new IOException("Modrinth filtering does not support loader: " + loader);
        };
    }

    private static void validateProjectId(String id) throws IOException {
        if (id == null || !id.matches("[A-Za-z0-9_-]+")) {
            throw new IOException("Modrinth returned an invalid project or version ID.");
        }
    }

    private static void validateFileName(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank() || fileName.equals(".") || fileName.equals("..")
                || fileName.contains("/") || fileName.contains("\\")
                || fileName.matches(".*[<>:\"|?*\\p{Cntrl}].*")) {
            throw new IOException("Modrinth returned an unsafe mod file name.");
        }
    }

    private static String requiredString(JsonObject object, String property) throws IOException {
        String value = optionalString(object, property);
        if (value == null || value.isBlank()) throw new IOException("Modrinth response is missing " + property + ".");
        return value;
    }

    private static String optionalString(JsonObject object, String property) {
        JsonElement value = object.get(property);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static void requireText(String value, String label) throws IOException {
        if (value == null || value.isBlank()) throw new IOException(label + " is not set for this instance.");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record Project(String projectId, String slug, String title, String description, long downloads) {
        @Override
        public String toString() {
            return title == null || title.isBlank() ? projectId : title;
        }
    }

    public record InstalledMod(String projectId, String title, String versionId, String versionNumber,
                               String fileName) {
        @Override
        public String toString() {
            String displayTitle = title == null || title.isBlank() ? projectId : title;
            return displayTitle + "  ·  " + (versionNumber == null ? versionId : versionNumber)
                    + "  (" + fileName + ")";
        }
    }

    public record ModUpdate(InstalledMod installed, String latestVersionId, String latestVersionNumber) {}
}
