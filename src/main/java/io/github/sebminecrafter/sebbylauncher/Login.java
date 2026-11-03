package io.github.sebminecrafter.sebbylauncher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.java.model.MinecraftProfile;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.github.sebminecrafter.sebbylauncher.GuiHelpers.open;
import static io.github.sebminecrafter.sebbylauncher.Main.logger;
import static io.github.sebminecrafter.sebbylauncher.Window.addItem;

@SuppressWarnings("unused")
public class Login {
    private static final String USER_AGENT = "MinecraftAuth/5.0.0";
    private static final String ENTITLEMENTS_URL = "https://api.minecraftservices.com/entitlements/mcstore";
    private static final String ACCOUNT_TYPE_OFFLINE = "offline";
    private static final String ACCOUNT_TYPE_MICROSOFT = "microsoft";

    private static final HttpClient AUTH_CLIENT = MinecraftAuth.createHttpClient(USER_AGENT);
    private static final java.net.http.HttpClient HTTP = java.net.http.HttpClient.newHttpClient();

    static final File tokenFile = LauncherDirs.TOKENS.resolve("tokens.json").toFile();
    static final File accountsFile = LauncherDirs.TOKENS.resolve("accounts.json").toFile();

    private static JavaAuthManager authManager;

    public record Account(String id, String name, String type, String uuid, String accessToken, String authJson) {
        public Account(String id, String name, String type, String uuid, String accessToken) {
            this(id, name, type, uuid, accessToken, null);
        }

        @Override
        public String toString() {
            String label = name == null || name.isBlank() ? "Unknown" : name;
            if (type == null || type.isBlank()) return label;
            return label + " (" + type + ")";
        }
    }

    /**
     * Prevent instantiation
     */
    public Login() {
        throw new UnsupportedOperationException(
                "Cannot be instantiated."
        );
    }

    public static List<Account> getAccounts() {
        JsonObject store = loadAccountStore();
        JsonArray accounts = store.has("accounts") ? store.getAsJsonArray("accounts") : null;
        if (accounts == null || accounts.isEmpty()) {
            return loadLegacyAccounts();
        }

        List<Account> saved = new ArrayList<>();
        for (JsonElement element : accounts) {
            JsonObject accountJson = element.getAsJsonObject();
            saved.add(new Account(
                    getString(accountJson, "id"),
                    getString(accountJson, "name"),
                    getString(accountJson, "type"),
                    getString(accountJson, "uuid"),
                    getString(accountJson, "accessToken"),
                    getString(accountJson, "authJson")
            ));
        }
        return saved;
    }

    public static Account getSelectedAccount() {
        JsonObject store = loadAccountStore();
        String selectedId = store.has("selectedAccountId") ? parseString(store.get("selectedAccountId")) : null;
        if (selectedId == null || selectedId.isBlank()) {
            List<Account> accounts = getAccounts();
            if (accounts.isEmpty()) return null;
            selectedId = accounts.getFirst().id();
            setSelectedAccount(selectedId);
            return accounts.getFirst();
        }
        final String chosenId = selectedId;

        return getAccounts().stream()
                .filter(account -> account.id() != null && account.id().equals(chosenId))
                .findFirst()
                .orElseGet(() -> {
                    List<Account> accounts = getAccounts();
                    if (accounts.isEmpty()) return null;
                    Account fallback = accounts.getFirst();
                    setSelectedAccount(fallback.id());
                    return fallback;
                });
    }

    public static String getSelectedAccountName() {
        Account account = getSelectedAccount();
        return account == null ? "No account" : account.name();
    }

    public static void setSelectedAccount(String accountId) {
        JsonObject store = loadAccountStore();
        if (accountId == null || accountId.isBlank()) {
            store.remove("selectedAccountId");
        } else {
            store.addProperty("selectedAccountId", accountId);
        }
        saveAccountStore(store);
    }

    public static boolean addOfflineAccount(String username) {
        String name = sanitizePlayerName(username);
        if (name == null || name.isBlank()) return false;

        String uuid = UUID.nameUUIDFromBytes(("Offline:" + name).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        String token = "offline:" + name + ":" + uuid;
        Account account = new Account(UUID.randomUUID().toString(), name, ACCOUNT_TYPE_OFFLINE, uuid, token);
        saveAccount(account);
        setSelectedAccount(account.id());
        return true;
    }

    public static boolean login() {
        AtomicReference<JButton> loginButton = new AtomicReference<>();
        JavaAuthManager manager;

        try {
            manager = JavaAuthManager.create(AUTH_CLIENT).login(
                    DeviceCodeMsaAuthService::new,
                    (Consumer<MsaDeviceCode>) deviceCode -> {
                        String url = deviceCode.getDirectVerificationUri();
                        logger.info("Login URL: " + url, "SebbyLauncher/Login");
                        loginButton.set(showLoginButton(url));
                    });
        } catch (IOException | TimeoutException e) {
            logError("Login failed.", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logError("Login was interrupted.", e);
            return false;
        } finally {
            JButton button = loginButton.get();
            if (button != null) SwingUtilities.invokeLater(() -> button.setVisible(false));
        }

        authManager = manager;
        try {
            MinecraftProfile profile = authManager.getMinecraftProfile().getUpToDate();
            Account account = new Account(
                    UUID.randomUUID().toString(),
                    profile.getName(),
                    ACCOUNT_TYPE_MICROSOFT,
                    profile.getId().toString().replace("-", ""),
                    authManager.getMinecraftToken().getUpToDate().getToken(),
                    JavaAuthManager.toJson(authManager).toString()
            );
            saveAccount(account);
            setSelectedAccount(account.id());
            return true;
        } catch (IOException e) {
            logError("Failed to load Microsoft profile.", e);
            return false;
        }
    }

    /**
     * Check whether an existing login token already exists and can be used.
     * @return a boolean value of whether there is an existing login (valid token) already.
     */
    public static boolean existingLogin() {
        Account selected = getSelectedAccount();
        if (selected == null) {
            return authManager != null;
        }
        return selected.accessToken() != null && (!ACCOUNT_TYPE_MICROSOFT.equals(selected.type()) || selected.authJson() != null || authManager != null);
    }

    /**
     * Gets a Minecraft token if there is an existing login
     * @return the Minecraft token as a {@link String} if it exists, {@code null} otherwise
     */
    public static String getMcToken() {
        Account selected = getSelectedAccount();
        if (selected == null) return null;

        if (ACCOUNT_TYPE_OFFLINE.equals(selected.type())) {
            return selected.accessToken();
        }

        JavaAuthManager manager = loadAccountManager(selected);
        if (manager == null) return null;
        authManager = manager;
        try {
            String token = manager.getMinecraftToken().getUpToDate().getToken();
            Account refreshed = new Account(selected.id(), selected.name(), selected.type(), selected.uuid(), token, JavaAuthManager.toJson(manager).toString());
            saveAccount(refreshed);
            return token;
        } catch (IOException e) {
            logError("Failed to get Minecraft token.", e);
            return null;
        }
    }

    /**
     * Gets the account details needed to start the game, if there is an existing login
     * @return a {@link Session}, or {@code null} if there is no login or the profile couldn't be loaded
     */
    public static Session getSession() {
        Account selected = getSelectedAccount();
        if (selected == null) return null;

        if (ACCOUNT_TYPE_OFFLINE.equals(selected.type())) {
            return new Session(selected.name(), selected.uuid(), selected.accessToken(), "legacy");
        }

        JavaAuthManager manager = loadAccountManager(selected);
        if (manager == null) return null;
        authManager = manager;
        try {
            String token = manager.getMinecraftToken().getUpToDate().getToken();
            MinecraftProfile profile = manager.getMinecraftProfile().getUpToDate();
            Account refreshed = new Account(selected.id(), profile.getName(), selected.type(), profile.getId().toString().replace("-", ""), token, JavaAuthManager.toJson(manager).toString());
            saveAccount(refreshed);
            return new Session(profile.getName(), profile.getId().toString().replace("-", ""), token, "msa");
        } catch (IOException e) {
            logError("Failed to get Minecraft profile.", e);
            return null;
        }
    }

    /**
     * Check whether a user (token) owns Minecraft
     * @param accessToken a valid access token for a Minecraft account
     * @return a boolean on whether the token owns a Minecraft account
     * @throws IOException when an http request or file operation fails
     * @throws InterruptedException when an http request is interrupted
     */
    public static boolean ownsMinecraft(String accessToken) throws IOException, InterruptedException {
        if (accessToken == null) return false;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENTITLEMENTS_URL))
                .header("Authorization", "Bearer " + accessToken)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return false;

        JsonArray items = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("items");
        if (items == null) return false;

        for (JsonElement item : items) {
            JsonElement name = item.getAsJsonObject().get("name");
            if (name != null && name.getAsString().toLowerCase().contains("minecraft")) return true;
        }
        return false;
    }

    /**
     * Attempts to log in to a Minecraft account
     * @return true if the login was successful, false otherwise
     */
    public static boolean attemptLogin() {
        Session session = getSession();
        if (session == null) {
            if (login()) session = getSession();
        }
        if (session == null) return false;

        if (ACCOUNT_TYPE_OFFLINE.equals(getSelectedAccount() == null ? null : getSelectedAccount().type())) {
            return true;
        }

        try {
            if (ownsMinecraft(session.accessToken())) return true;
            logError("Couldn't verify game ownership!", null);
        } catch (IOException e) {
            logError("Failed to check game ownership.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logError("Ownership check was interrupted.", e);
        }
        return false;
    }

    public static void saveLegacyLogin(JavaAuthManager manager) {
        if (manager == null) return;
        authManager = manager;
        try {
            MinecraftProfile profile = manager.getMinecraftProfile().getUpToDate();
            Account account = new Account(UUID.randomUUID().toString(), profile.getName(), ACCOUNT_TYPE_MICROSOFT,
                    profile.getId().toString().replace("-", ""), manager.getMinecraftToken().getUpToDate().getToken(),
                    JavaAuthManager.toJson(manager).toString());
            saveAccount(account);
            setSelectedAccount(account.id());
        } catch (IOException e) {
            logError("Failed to save legacy Microsoft login.", e);
        }
    }

    /**
     * Log an error
     * @param message the message to log with the error
     * @param e the exception
     */
    private static void logError(String message, Exception e) {
        if (message != null) logger.error(message, "SebbyLauncher/Login");
        if (e != null) logger.trace(Arrays.toString(e.getStackTrace()), "SebbyLauncher/Login");
    }

    /**
     * Adds a login button to the window
     * @param url the login url that the button should open
     * @return the created {@link JButton} instance
     */
    private static JButton showLoginButton(String url) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.anchor = GridBagConstraints.CENTER;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.weighty = 0;
        constraints.insets = new Insets(10, 0, 0, 0);

        JButton button = new JButton("Login");
        button.setToolTipText(url);
        button.addActionListener(_ -> open(URI.create(url)));
        SwingUtilities.invokeLater(() -> addItem(button, constraints));
        return button;
    }

    private static JsonObject loadAccountStore() {
        if (!accountsFile.isFile()) {
            return new JsonObject();
        }

        try (Reader reader = Files.newBufferedReader(accountsFile.toPath())) {
            JsonElement json = JsonParser.parseReader(reader);
            return json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
        } catch (IOException | RuntimeException e) {
            logError("Saved accounts are missing or unreadable.", e);
            return new JsonObject();
        }
    }

    private static void saveAccountStore(JsonObject store) {
        try (Writer writer = Files.newBufferedWriter(accountsFile.toPath())) {
            writer.write(store.toString());
        } catch (IOException e) {
            logError("Failed to save account details.", e);
        }
    }

    private static void saveAccount(Account account) {
        JsonObject store = loadAccountStore();
        JsonArray accounts = store.has("accounts") ? store.getAsJsonArray("accounts") : new JsonArray();
        List<Account> current = new ArrayList<>();
        for (JsonElement element : accounts) {
            JsonObject json = element.getAsJsonObject();
            current.add(new Account(getString(json, "id"), getString(json, "name"), getString(json, "type"),
                    getString(json, "uuid"), getString(json, "accessToken"), getString(json, "authJson")));
        }

        Optional<Account> existing = current.stream().filter(candidate -> Objects.equals(candidate.id(), account.id())).findFirst();
        if (existing.isPresent()) {
            current.removeIf(candidate -> Objects.equals(candidate.id(), account.id()));
        }
        current.add(account);

        JsonArray updated = new JsonArray();
        for (Account item : current) {
            updated.add(accountToJson(item));
        }
        store.add("accounts", updated);
        saveAccountStore(store);
    }

    private static JsonObject accountToJson(Account account) {
        JsonObject json = new JsonObject();
        if (account.id() != null) json.addProperty("id", account.id());
        if (account.name() != null) json.addProperty("name", account.name());
        if (account.type() != null) json.addProperty("type", account.type());
        if (account.uuid() != null) json.addProperty("uuid", account.uuid());
        if (account.accessToken() != null) json.addProperty("accessToken", account.accessToken());
        if (account.authJson() != null) json.addProperty("authJson", account.authJson());
        return json;
    }

    private static JavaAuthManager loadAccountManager(Account account) {
        if (account == null || account.authJson() == null || account.authJson().isBlank()) {
            if (authManager != null) {
                return authManager;
            }
            return loadSavedLogin();
        }
        try {
            return JavaAuthManager.fromJson(AUTH_CLIENT, JsonParser.parseString(account.authJson()).getAsJsonObject());
        } catch (RuntimeException e) {
            logError("Saved Microsoft account could not be loaded.", e);
            return null;
        }
    }

    private static JavaAuthManager loadSavedLogin() {
        if (!tokenFile.isFile()) return null;

        try (Reader reader = Files.newBufferedReader(tokenFile.toPath())) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return JavaAuthManager.fromJson(AUTH_CLIENT, json);
        } catch (IOException | RuntimeException e) {
            logError("Saved credentials are missing or unreadable.", e);
            return null;
        }
    }

    private static List<Account> loadLegacyAccounts() {
        JavaAuthManager manager = loadSavedLogin();
        if (manager == null) return List.of();

        try {
            MinecraftProfile profile = manager.getMinecraftProfile().getUpToDate();
            String token = manager.getMinecraftToken().getUpToDate().getToken();
            Account account = new Account(UUID.randomUUID().toString(), profile.getName(), ACCOUNT_TYPE_MICROSOFT,
                    profile.getId().toString().replace("-", ""), token, JavaAuthManager.toJson(manager).toString());
            saveAccount(account);
            setSelectedAccount(account.id());
            return List.of(account);
        } catch (IOException e) {
            logError("Failed to import legacy login.", e);
            return List.of();
        }
    }

    private static String parseString(JsonElement element) {
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static String getString(JsonObject json, String key) {
        return json.has(key) ? parseString(json.get(key)) : null;
    }

    private static String sanitizePlayerName(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.isEmpty()) return "";
        value = value.replaceAll("[^A-Za-z0-9_]+", "");
        return value.length() > 16 ? value.substring(0, 16) : value;
    }
}