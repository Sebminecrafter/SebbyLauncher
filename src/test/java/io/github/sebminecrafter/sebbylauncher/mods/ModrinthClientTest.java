package io.github.sebminecrafter.sebbylauncher.mods;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.sebminecrafter.sebbylauncher.Instance;
import io.github.sebminecrafter.sebbylauncher.LauncherDetails;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModrinthClientTest {
    @Test
    void searchFiltersByMinecraftVersionAndLoaderAndSendsVersionedUserAgent() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> userAgent = new AtomicReference<>();
        try (TestServer server = new TestServer(exchange -> {
            requestPath.set(exchange.getRequestURI().toString());
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            respond(exchange, """
                    {"hits":[{"project_id":"abc123","slug":"example","title":"Example Mod",
                    "description":"A test mod","downloads":12}]}
                    """);
        })) {
            ModrinthClient client = new ModrinthClient(
                    java.net.http.HttpClient.newHttpClient(), server.apiBase());

            var results = client.search("example mod", "1.20.1", "Fabric");

            assertEquals(1, results.size());
            assertEquals("abc123", results.getFirst().projectId());
            assertEquals(LauncherDetails.NAME + "/" + LauncherDetails.VERSION, userAgent.get());
            String decodedQuery = URLDecoder.decode(requestPath.get(), StandardCharsets.UTF_8);
            assertTrue(decodedQuery.contains("versions:1.20.1"));
            assertTrue(decodedQuery.contains("categories:fabric"));
            assertTrue(decodedQuery.contains("project_type:mod"));
        }
    }

    @Test
    void vanillaSearchDoesNotAddLoaderFacet() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        try (TestServer server = new TestServer(exchange -> {
            requestPath.set(exchange.getRequestURI().toString());
            respond(exchange, "{\"hits\":[]}");
        })) {
            ModrinthClient client = new ModrinthClient(
                    java.net.http.HttpClient.newHttpClient(), server.apiBase());

            assertEquals(0, client.search("", "1.21", "Vanilla").size());

            String decodedQuery = URLDecoder.decode(requestPath.get(), StandardCharsets.UTF_8);
            assertTrue(decodedQuery.contains("versions:1.21"));
            assertFalse(decodedQuery.contains("categories:"));
        }
    }

    @Test
    void listInstalledReturnsEmptyWhenInstanceHasNoModsDirectory() throws Exception {
        Path gameFolder = Files.createTempDirectory("sebby-mods-test");
        try {
            Instance instance = new Instance("Test", "1.20.1", "Fabric", "", 2048, java.util.List.of(),
                    gameFolder.toString());
            ModrinthClient client = new ModrinthClient();

            assertEquals(0, client.listInstalled(instance).size());
        } finally {
            Files.deleteIfExists(gameFolder);
        }
    }

    private static void respond(HttpExchange exchange, String body)
            throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    @FunctionalInterface
    private interface RequestHandler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException;
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
