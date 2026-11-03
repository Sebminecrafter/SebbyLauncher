package io.github.sebminecrafter.sebbylauncher.mods;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.sebminecrafter.sebbylauncher.Instance;
import io.github.sebminecrafter.sebbylauncher.LauncherDetails;
import io.github.sebminecrafter.sebbylauncher.mods.ModrinthClient.ContentType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModrinthClientTest {
    @Test
    void modSearchFiltersByMinecraftVersionAndLoaderAndSendsVersionedUserAgent() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> userAgent = new AtomicReference<>();
        try (TestServer server = new TestServer(exchange -> {
            requestPath.set(exchange.getRequestURI().toString());
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            respond(exchange, """
                    {"hits":[{"project_id":"abc123","slug":"example","title":"Example Mod",
                    "description":"A test mod","icon_url":"https://cdn.modrinth.com/icon.png",
                    "downloads":12}]}
                    """);
        })) {
            ModrinthClient client = new ModrinthClient(
                    java.net.http.HttpClient.newHttpClient(), server.apiBase());

            var results = client.search("example mod", "1.20.1", "Fabric", ContentType.MOD);

            assertEquals(1, results.size());
            assertEquals("abc123", results.getFirst().projectId());
            assertEquals("https://cdn.modrinth.com/icon.png", results.getFirst().iconUrl());
            assertEquals(ContentType.MOD, results.getFirst().type());
            assertEquals(LauncherDetails.NAME + "/" + LauncherDetails.VERSION, userAgent.get());
            String decodedQuery = URLDecoder.decode(requestPath.get(), StandardCharsets.UTF_8);
            assertTrue(decodedQuery.contains("versions:1.20.1"));
            assertTrue(decodedQuery.contains("categories:fabric"));
            assertTrue(decodedQuery.contains("project_type:mod"));
        }
    }

    @Test
    void packSearchUsesProjectTypeAndDoesNotFilterByModLoader() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        try (TestServer server = new TestServer(exchange -> {
            requestPath.set(exchange.getRequestURI().toString());
            respond(exchange, "{\"hits\":[]}");
        })) {
            ModrinthClient client = new ModrinthClient(
                    java.net.http.HttpClient.newHttpClient(), server.apiBase());

            assertEquals(0, client.search("", "1.21", "Fabric", ContentType.SHADER_PACK).size());

            String decodedQuery = URLDecoder.decode(requestPath.get(), StandardCharsets.UTF_8);
            assertTrue(decodedQuery.contains("query=&limit=20"));
            assertTrue(decodedQuery.contains("versions:1.21"));
            assertTrue(decodedQuery.contains("project_type:shader"));
            assertFalse(decodedQuery.contains("categories:"));
        }
    }

    @Test
    void listInstalledScansFilesWithoutRegistryAndSupportsPackDirectories() throws Exception {
        Path gameFolder = Files.createTempDirectory("sebby-content-test");
        try {
            Instance instance = instance(gameFolder);
            Path mods = Files.createDirectories(gameFolder.resolve("mods"));
            Path packs = Files.createDirectories(gameFolder.resolve("datapacks"));
            Path unpackedPack = Files.createDirectories(packs.resolve("unpacked-world-pack"));
            Files.writeString(unpackedPack.resolve("pack.mcmeta"), "{}");
            Files.writeString(mods.resolve("example.jar"), "installed mod");
            Files.writeString(mods.resolve("disabled.jar.disabled"), "disabled mod");
            Files.writeString(mods.resolve("notes.txt"), "not a mod");
            Files.writeString(mods.resolve(".sebbylauncher-mods.json"), "legacy registry");
            Files.writeString(packs.resolve("world-pack.zip"), "data pack");
            ModrinthClient client = new ModrinthClient();

            List<ModrinthClient.InstalledContent> installedMods =
                    client.listInstalled(instance, ContentType.MOD);
            List<ModrinthClient.InstalledContent> installedPacks =
                    client.listInstalled(instance, ContentType.DATA_PACK);

            assertEquals(2, installedMods.size());
            assertTrue(installedMods.stream().anyMatch(content -> content.fileName().equals("example.jar")
                    && content.enabled()));
            assertTrue(installedMods.stream().anyMatch(content -> content.fileName().equals("disabled.jar.disabled")
                    && !content.enabled()));
            assertFalse(Files.exists(mods.resolve(".sebbylauncher-mods.json")));
            assertEquals(2, installedPacks.size());
            ModrinthClient.InstalledContent zipPack = installedPacks.stream()
                    .filter(content -> content.fileName().equals("world-pack.zip")).findFirst().orElseThrow();
            assertEquals(40, zipPack.sha1().length());
            assertTrue(installedPacks.stream()
                    .anyMatch(content -> content.fileName().equals("unpacked-world-pack")
                            && content.sha1() == null));
        } finally {
            deleteTree(gameFolder);
        }
    }

    @Test
    void hashLookupFindsCompatibleUpdateWithoutAnInstalledModRegistry() throws Exception {
        Path gameFolder = Files.createTempDirectory("sebby-hash-test");
        try {
            Path mods = Files.createDirectories(gameFolder.resolve("mods"));
            byte[] installedBytes = "installed mod file".getBytes(StandardCharsets.UTF_8);
            Files.write(mods.resolve("example.jar"), installedBytes);
            String installedHash = sha1(installedBytes);
            AtomicReference<String> versionFilesBody = new AtomicReference<>();
            AtomicReference<String> latestVersionPath = new AtomicReference<>();
            try (TestServer server = new TestServer(exchange -> {
                String path = exchange.getRequestURI().getPath();
                if (path.endsWith("/version_files")) {
                    versionFilesBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    respond(exchange, """
                            {"%s":{"id":"oldver","project_id":"abc123","version_number":"1.0"}}
                            """.formatted(installedHash));
                } else if (path.endsWith("/project/abc123")) {
                    respond(exchange, """
                            {"project_type":"mod","title":"Example Mod"}
                            """);
                } else if (path.endsWith("/project/abc123/version")) {
                    latestVersionPath.set(exchange.getRequestURI().toString());
                    respond(exchange, """
                            [{"id":"newver","version_number":"2.0","files":[]}]
                            """);
                } else {
                    respond(exchange, 404, "{}");
                }
            })) {
                ModrinthClient client = new ModrinthClient(
                        java.net.http.HttpClient.newHttpClient(), server.apiBase());

                List<ModrinthClient.ContentUpdate> updates =
                        client.checkForUpdates(instance(gameFolder), ContentType.MOD);

                assertEquals(1, updates.size());
                assertEquals("oldver", updates.getFirst().installed().versionId());
                assertEquals("Example Mod", updates.getFirst().installed().title());
                assertEquals("newver", updates.getFirst().latestVersionId());
                assertTrue(versionFilesBody.get().contains(installedHash));
                assertTrue(versionFilesBody.get().contains("\"sha1\""));
                assertTrue(latestVersionPath.get().contains("loaders="));
            }
        } finally {
            deleteTree(gameFolder);
        }
    }

    @Test
    void setEnabledAndRemoveOperateOnLocalFiles() throws Exception {
        Path gameFolder = Files.createTempDirectory("sebby-enable-test");
        try {
            Path mods = Files.createDirectories(gameFolder.resolve("mods"));
            Path file = mods.resolve("example.jar");
            Files.writeString(file, "mod");
            ModrinthClient client = new ModrinthClient();
            Instance instance = instance(gameFolder);
            ModrinthClient.InstalledContent installed = client.listInstalled(instance, ContentType.MOD).getFirst();

            client.setEnabled(instance, installed, false);

            assertFalse(Files.exists(file));
            Path disabled = mods.resolve("example.jar.disabled");
            assertTrue(Files.exists(disabled));
            ModrinthClient.InstalledContent disabledContent =
                    client.listInstalled(instance, ContentType.MOD).getFirst();
            client.setEnabled(instance, disabledContent, true);
            assertTrue(Files.exists(file));

            client.remove(instance, client.listInstalled(instance, ContentType.MOD).getFirst());
            assertFalse(Files.exists(file));

            Path dataPacks = Files.createDirectories(gameFolder.resolve("datapacks"));
            Path unpacked = Files.createDirectories(dataPacks.resolve("custom-pack"));
            Files.writeString(unpacked.resolve("pack.mcmeta"), "{}");
            ModrinthClient.InstalledContent unpackedPack =
                    client.listInstalled(instance, ContentType.DATA_PACK).getFirst();
            client.setEnabled(instance, unpackedPack, false);
            assertTrue(Files.isDirectory(dataPacks.resolve("custom-pack.disabled")));
            client.remove(instance,
                    client.listInstalled(instance, ContentType.DATA_PACK).getFirst());
            assertFalse(Files.exists(dataPacks.resolve("custom-pack.disabled")));
        } finally {
            deleteTree(gameFolder);
        }
    }

    @Test
    void listInstalledReturnsEmptyWhenInstanceHasNoModsDirectory() throws Exception {
        Path gameFolder = Files.createTempDirectory("sebby-mods-test");
        try {
            ModrinthClient client = new ModrinthClient();

            assertEquals(0, client.listInstalled(instance(gameFolder), ContentType.MOD).size());
        } finally {
            deleteTree(gameFolder);
        }
    }

    private static Instance instance(Path gameFolder) {
        return new Instance("Test", "1.20.1", "Fabric", "", 2048, List.of(), gameFolder.toString());
    }

    private static String sha1(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(value));
    }

    private static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        respond(exchange, 200, body);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, response.length);
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    @FunctionalInterface
    private interface RequestHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;

        TestServer(RequestHandler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v2/", handler::handle);
            server.start();
        }

        URI apiBase() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v2/");
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
