package io.github.sebminecrafter.sebbylauncher.mods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sebminecrafter.sebbylauncher.Instance;
import io.github.sebminecrafter.sebbylauncher.LauncherDetails;
import io.github.sebminecrafter.sebbylauncher.downloader.Downloader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.Map;
import java.util.Objects;

public final class ModrinthClient {
    private static final URI API_BASE = URI.create("https://api.modrinth.com/v2/");
    private static final String USER_AGENT = LauncherDetails.NAME + "/" + LauncherDetails.VERSION;
    private static final long MAX_API_RESPONSE_BYTES = 16L * 1024 * 1024;
    private static final long MAX_FILE_BYTES = 512L * 1024 * 1024;
    private static final String LEGACY_REGISTRY_FILE = ".sebbylauncher-mods.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
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

    public List<Project> search(String query, String gameVersion, String loader, ContentType type)
            throws IOException, InterruptedException {
        Objects.requireNonNull(type, "Content type");
        String resolvedVersion = resolveMinecraftVersion(gameVersion);
        List<List<String>> facets = new ArrayList<>();
        facets.add(List.of("project_type:" + type.modrinthId()));
        facets.add(List.of("versions:" + resolvedVersion));
        String loaderId = type == ContentType.MOD ? loaderId(loader) : null;
        if (loaderId != null) facets.add(List.of("categories:" + loaderId));

        String url = "search?query=" + encode(query == null ? "" : query.trim())
                + "&limit=20&facets=" + encode(GSON.toJson(facets));
        JsonArray hits = getJsonObject(apiBase.resolve(url)).getAsJsonArray("hits");
        List<Project> projects = new ArrayList<>();
        for (JsonElement element : hits) {
            JsonObject hit = element.getAsJsonObject();
            projects.add(new Project(
                    type,
                    requiredString(hit, "project_id"),
                    optionalString(hit, "slug"),
                    optionalString(hit, "title"),
                    optionalString(hit, "description"),
                    optionalString(hit, "icon_url"),
                    hit.has("downloads") && !hit.get("downloads").isJsonNull()
                            ? hit.get("downloads").getAsLong() : 0));
        }
        return projects;
    }

    public List<InstalledContent> listInstalled(Instance instance, ContentType type) throws IOException {
        Path directory = contentDirectory(instance, type);
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("The " + type.directoryName() + " folder must not be a symbolic link.");
        }
        if (type == ContentType.MOD) removeLegacyRegistry(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return List.of();

        List<InstalledContent> installed = new ArrayList<>();
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                if (Files.isSymbolicLink(file)) continue;
                String fileName = file.getFileName().toString();
                boolean directoryPack = type != ContentType.MOD
                        && Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS);
                boolean regularFile = Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS);
                if (!directoryPack && (!regularFile || isNotManagedFileName(type, fileName))) continue;
                installed.add(new InstalledContent(type, fileName, regularFile ? sha1(file) : null,
                        !fileName.endsWith(".disabled"),
                        null, null, null, null));
            }
        }
        return List.copyOf(installed);
    }

    public List<ContentUpdate> checkForUpdates(Instance instance, ContentType type)
            throws IOException, InterruptedException {
        List<InstalledContent> installed = listInstalled(instance, type);
        if (installed.isEmpty()) return List.of();

        List<String> hashes = installed.stream().map(InstalledContent::sha1)
                .filter(Objects::nonNull).distinct().toList();
        if (hashes.isEmpty()) return List.of();
        Map<String, JsonElement> versionsByHash = findVersionsByHashes(hashes);
        String gameVersion = resolveMinecraftVersion(instance.version());
        Map<String, JsonObject> projectsById = new java.util.HashMap<>();
        List<ContentUpdate> updates = new ArrayList<>();

        for (InstalledContent content : installed) {
            JsonElement currentVersionElement = versionsByHash.get(content.sha1());
            if (currentVersionElement == null || !currentVersionElement.isJsonObject()) continue;
            JsonObject currentVersion = currentVersionElement.getAsJsonObject();
            String projectId = requiredString(currentVersion, "project_id");
            validateProjectId(projectId);
            JsonObject project = projectsById.get(projectId);
            if (project == null) {
                project = getJsonObject(apiBase.resolve("project/" + projectId));
                projectsById.put(projectId, project);
            }
            if (!type.modrinthId().equals(requiredString(project, "project_type"))) continue;

            JsonObject latest = latestCompatibleVersion(projectId, gameVersion, instance.loader(), type);
            if (latest == null) continue;
            String currentVersionId = requiredString(currentVersion, "id");
            String latestVersionId = requiredString(latest, "id");
            if (latestVersionId.equals(currentVersionId)) continue;

            InstalledContent identified = new InstalledContent(type, content.fileName(), content.sha1(),
                    content.enabled(), projectId,
                    optionalString(project, "title"),
                    currentVersionId, optionalString(currentVersion, "version_number"));
            updates.add(new ContentUpdate(identified, latestVersionId, optionalString(latest, "version_number")));
        }
        return List.copyOf(updates);
    }

    public InstalledContent install(Instance instance, Project project) throws IOException, InterruptedException {
        String gameVersion = resolveMinecraftVersion(instance.version());
        JsonObject version = latestCompatibleVersion(project.projectId(), gameVersion, instance.loader(), project.type());
        if (version == null) {
            throw new IOException("No compatible version of " + project.title() + " was found.");
        }
        return installVersion(instance, project.type(), project.projectId(), project.title(), version, null);
    }

    public void update(Instance instance, ContentUpdate update) throws IOException, InterruptedException {
        JsonObject version = getVersion(update.latestVersionId());
        installVersion(instance, update.installed().type(), update.installed().projectId(),
                update.installed().title(), version, update.installed());
    }

    public void setEnabled(Instance instance, InstalledContent content, boolean enabled) throws IOException {
        if (content.enabled() == enabled) return;
        Path directory = contentDirectory(instance, content.type());
        Path source = resolveManagedPath(directory, content.type(), content.fileName());
        String destinationName = enabled
                ? content.fileName().substring(0, content.fileName().length() - ".disabled".length())
                : content.fileName() + ".disabled";
        validateFileName(destinationName);
        Path destination = directory.resolve(destinationName);
        if (Files.isSymbolicLink(destination)) {
            throw new IOException("Refusing to replace a symbolic link: " + destinationName);
        }
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("A file already exists at " + destinationName + ".");
        }
        moveWithoutReplacing(source, destination);
    }

    public void remove(Instance instance, InstalledContent content) throws IOException {
        Path directory = contentDirectory(instance, content.type());
        Path path = resolveManagedPath(directory, content.type(), content.fileName());
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (var paths = Files.walk(path)) {
                for (Path child : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(child);
                }
            }
        } else {
            Files.delete(path);
        }
    }

    private InstalledContent installVersion(Instance instance, ContentType type, String projectId,
                                            String title, JsonObject version, InstalledContent previous)
            throws IOException, InterruptedException {
        validateProjectId(projectId);
        JsonObject selectedFile = primaryFile(version);
        String downloadName = requiredString(selectedFile, "filename");
        validateFileName(downloadName);
        String requiredExtension = type == ContentType.MOD ? ".jar" : ".zip";
        if (!downloadName.toLowerCase(Locale.ROOT).endsWith(requiredExtension)) {
            throw new IOException("Refusing to install a " + type.label() + " file that is not a "
                    + requiredExtension.toUpperCase(Locale.ROOT) + ": " + downloadName);
        }

        URI downloadUri;
        try {
            downloadUri = URI.create(requiredString(selectedFile, "url"));
        } catch (IllegalArgumentException e) {
            throw new IOException("Modrinth returned an invalid download URL.", e);
        }
        if (!"https".equalsIgnoreCase(downloadUri.getScheme()) || downloadUri.getHost() == null
                || downloadUri.getUserInfo() != null) {
            throw new IOException("Refusing an unsafe Modrinth download URL.");
        }
        JsonObject hashes = selectedFile.has("hashes") && selectedFile.get("hashes").isJsonObject()
                ? selectedFile.getAsJsonObject("hashes") : new JsonObject();
        if (!hashes.has("sha512") && !hashes.has("sha1")) {
            throw new IOException("Modrinth did not provide a checksum for " + downloadName + ".");
        }

        Path directory = contentDirectory(instance, type);
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("The " + type.directoryName() + " folder must not be a symbolic link.");
        }
        boolean enabled = previous == null || previous.enabled();
        String storedName = downloadName + (enabled ? "" : ".disabled");
        Path destination = chooseDestination(directory, storedName, projectId, previous);
        Path temporaryFile = Files.createTempFile(directory, ".sebbylauncher-", ".tmp");
        try {
            downloadFile(downloadUri, temporaryFile, hashes);
            moveReplacing(temporaryFile, destination);
            if (previous != null && !previous.fileName().equals(destination.getFileName().toString())) {
                Files.delete(resolveManagedPath(directory, type, previous.fileName()));
            }
            return new InstalledContent(type, destination.getFileName().toString(),
                    sha1(destination), enabled, projectId, title,
                    requiredString(version, "id"), optionalString(version, "version_number"));
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private JsonObject latestCompatibleVersion(String projectId, String gameVersion, String loader,
                                               ContentType type)
            throws IOException, InterruptedException {
        validateProjectId(projectId);
        requireText(gameVersion, "Minecraft version");
        String url = "project/" + projectId + "/version?game_versions=" + encode(GSON.toJson(List.of(gameVersion)));
        String loaderId = type == ContentType.MOD ? loaderId(loader) : null;
        if (loaderId != null) url += "&loaders=" + encode(GSON.toJson(List.of(loaderId)));
        JsonArray versions = getJsonArray(apiBase.resolve(url));
        return versions.isEmpty() ? null : versions.get(0).getAsJsonObject();
    }

    private Map<String, JsonElement> findVersionsByHashes(List<String> hashes)
            throws IOException, InterruptedException {
        Map<String, JsonElement> versions = new java.util.HashMap<>();
        for (int start = 0; start < hashes.size(); start += 100) {
            List<String> batch = hashes.subList(start, Math.min(start + 100, hashes.size()));
            JsonObject requestBody = new JsonObject();
            JsonArray hashArray = new JsonArray();
            batch.forEach(hashArray::add);
            requestBody.add("hashes", hashArray);
            requestBody.addProperty("algorithm", "sha1");

            HttpRequest request = request(apiBase.resolve("version_files"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody), StandardCharsets.UTF_8))
                    .build();
            JsonObject response = sendJsonObject(request);
            for (String hash : batch) {
                if (response.has(hash) && !response.get(hash).isJsonNull()) {
                    versions.put(hash, response.get(hash));
                }
            }
        }
        return versions;
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
        return parseObject(getBytes(uri));
    }

    private JsonArray getJsonArray(URI uri) throws IOException, InterruptedException {
        JsonElement element = JsonParser.parseString(new String(getBytes(uri), StandardCharsets.UTF_8));
        if (!element.isJsonArray()) throw new IOException("Modrinth returned an invalid JSON array.");
        return element.getAsJsonArray();
    }

    private JsonObject sendJsonObject(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Modrinth returned HTTP " + response.statusCode() + ".");
            }
            return parseObject(readLimited(body));
        }
    }

    private byte[] getBytes(URI uri) throws IOException, InterruptedException {
        HttpRequest request = request(uri).GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Modrinth returned HTTP " + response.statusCode() + " for " + uri.getPath());
            }
            return readLimited(body);
        }
    }

    private static JsonObject parseObject(byte[] data) throws IOException {
        JsonElement element = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
        if (!element.isJsonObject()) throw new IOException("Modrinth returned an invalid JSON object.");
        return element.getAsJsonObject();
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        byte[] data = input.readNBytes((int) ModrinthClient.MAX_API_RESPONSE_BYTES + 1);
        if (data.length > ModrinthClient.MAX_API_RESPONSE_BYTES) throw new IOException("Modrinth response exceeded the size limit.");
        return data;
    }

    private HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json");
    }

    private void downloadFile(URI uri, Path destination, JsonObject hashes)
            throws IOException, InterruptedException {
        HttpRequest request = request(uri).GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Modrinth download returned HTTP " + response.statusCode() + ".");
            }
            if (response.headers().firstValueAsLong("Content-Length").orElse(0) > MAX_FILE_BYTES) {
                throw new IOException("Downloaded file exceeds the 512 MB limit.");
            }
            String algorithm = hashes.has("sha512") ? "SHA-512" : "SHA-1";
            String hashName = algorithm.equals("SHA-512") ? "sha512" : "sha1";
            String expectedHash = requiredString(hashes, hashName);
            MessageDigest digest = newDigest(algorithm);
            long total = 0;
            byte[] buffer = new byte[32 * 1024];
            try (OutputStream output = Files.newOutputStream(destination)) {
                int count;
                while ((count = body.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_FILE_BYTES) throw new IOException("Downloaded file exceeds the 512 MB limit.");
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
            }
            if (!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expectedHash)) {
                throw new IOException("Downloaded file failed its " + algorithm + " checksum.");
            }
        }
    }

    private static String sha1(Path file) throws IOException {
        MessageDigest digest = newDigest("SHA-1");
        long total = 0;
        byte[] buffer = new byte[32 * 1024];
        try (InputStream input = Files.newInputStream(file)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_FILE_BYTES) throw new IOException("Installed file exceeds the 512 MB scan limit: "
                        + file.getFileName());
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newDigest(String algorithm) throws IOException {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Checksum algorithm is unavailable: " + algorithm, e);
        }
    }

    private static Path contentDirectory(Instance instance, ContentType type) throws IOException {
        try {
            Path gameDirectory = instance.gameDirectory().toAbsolutePath().normalize();
            if (Files.isSymbolicLink(gameDirectory)) {
                throw new IOException("The instance game folder must not be a symbolic link.");
            }
            Path directory = gameDirectory.resolve(type.directoryName()).normalize();
            if (!directory.startsWith(gameDirectory)) throw new IOException("The content folder is invalid.");
            return directory;
        } catch (RuntimeException e) {
            throw new IOException("The instance game folder is invalid.", e);
        }
    }

    private static Path resolveManagedPath(Path directory, ContentType type, String fileName) throws IOException {
        validateFileName(fileName);
        Path file = directory.resolve(fileName).normalize();
        if (!file.getParent().equals(directory)) throw new IOException("The installed file path is invalid.");
        if (Files.isSymbolicLink(file)) throw new IOException("Refusing to access a symbolic link: " + fileName);
        boolean directoryPack = type != ContentType.MOD && Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS);
        if (directoryPack) return file;
        if (isNotManagedFileName(type, fileName)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Installed file is missing: " + fileName);
        }
        return file;
    }

    private static boolean isNotManagedFileName(ContentType type, String fileName) {
        String activeName = fileName.endsWith(".disabled")
                ? fileName.substring(0, fileName.length() - ".disabled".length()) : fileName;
        String extension = type == ContentType.MOD ? ".jar" : ".zip";
        return !activeName.toLowerCase(Locale.ROOT).endsWith(extension);
    }

    private static JsonObject primaryFile(JsonObject version) throws IOException {
        if (!version.has("files") || !version.get("files").isJsonArray()) {
            throw new IOException("Modrinth returned a version without files.");
        }
        JsonArray files = version.getAsJsonArray("files");
        if (files.isEmpty()) throw new IOException("Modrinth returned a version without files.");
        JsonObject selected = null;
        for (JsonElement element : files) {
            JsonObject file = element.getAsJsonObject();
            if (file.has("primary") && file.get("primary").getAsBoolean()) return file;
            if (selected == null) selected = file;
        }
        if (selected == null) throw new IOException("Modrinth returned a version without a downloadable file.");
        return selected;
    }

    private static Path chooseDestination(Path directory, String fileName, String projectId,
                                         InstalledContent previous) throws IOException {
        Path destination = directory.resolve(fileName);
        if (Files.isSymbolicLink(destination)) {
            throw new IOException("Refusing to replace a symbolic link: " + fileName);
        }
        if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
                || previous != null && previous.fileName().equals(fileName)) return destination;

        boolean disabled = fileName.endsWith(".disabled");
        String activeName = disabled ? fileName.substring(0, fileName.length() - ".disabled".length()) : fileName;
        int extensionIndex = activeName.lastIndexOf('.');
        String alternative = activeName.substring(0, extensionIndex) + "-" + projectId
                + activeName.substring(extensionIndex) + (disabled ? ".disabled" : "");
        Path alternativePath = directory.resolve(alternative);
        if (Files.isSymbolicLink(alternativePath)) {
            throw new IOException("Refusing to replace a symbolic link: " + alternative);
        }
        if (Files.exists(alternativePath, LinkOption.NOFOLLOW_LINKS)
                && (previous == null || !previous.fileName().equals(alternative))) {
            throw new IOException("A file already exists at " + alternative + ".");
        }
        return alternativePath;
    }

    private static void removeLegacyRegistry(Path directory) throws IOException {
        Path registry = directory.resolve(LEGACY_REGISTRY_FILE);
        if (!Files.exists(registry, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(registry)) {
            throw new IOException("The legacy installed-mod registry must not be a symbolic link.");
        }
        Files.delete(registry);
    }

    private static void moveReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void moveWithoutReplacing(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination);
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
            throw new IOException("Modrinth returned an unsafe file name.");
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

    public enum ContentType {
        MOD("Mod", "mods", "mod"),
        RESOURCE_PACK("Resource pack", "resourcepacks", "resourcepack"),
        DATA_PACK("Data pack", "datapacks", "datapack"),
        SHADER_PACK("Shader pack", "shaderpacks", "shader");

        private final String label;
        private final String directoryName;
        private final String modrinthId;

        ContentType(String label, String directoryName, String modrinthId) {
            this.label = label;
            this.directoryName = directoryName;
            this.modrinthId = modrinthId;
        }

        public String label() {
            return label;
        }

        public String pluralLabel() {
            return switch (this) {
                case MOD -> "mods";
                case RESOURCE_PACK -> "resource packs";
                case DATA_PACK -> "data packs";
                case SHADER_PACK -> "shader packs";
            };
        }

        public String directoryName() {
            return directoryName;
        }

        public String modrinthId() {
            return modrinthId;
        }
    }

    public record Project(ContentType type, String projectId, String slug, String title,
                          String description, String iconUrl, long downloads) {
        @Override
        public String toString() {
            return title == null || title.isBlank() ? projectId : title;
        }
    }

    public record InstalledContent(ContentType type, String fileName, String sha1, boolean enabled,
                                   String projectId, String title, String versionId, String versionNumber) {
        public InstalledContent withEnabled(boolean newEnabled) {
            return new InstalledContent(type, fileName, sha1, newEnabled, projectId, title, versionId, versionNumber);
        }

        @Override
        public String toString() {
            String displayTitle = title == null || title.isBlank() ? fileName : title;
            String version = versionNumber == null ? "" : "  ·  " + versionNumber;
            return displayTitle + version + "  (" + fileName + ")";
        }
    }

    public record ContentUpdate(InstalledContent installed, String latestVersionId, String latestVersionNumber) {}
}
