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
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.github.sebminecrafter.sebbylauncher.GuiHelpers.open;
import static io.github.sebminecrafter.sebbylauncher.Main.logger;
import static io.github.sebminecrafter.sebbylauncher.Window.addItem;

public class Login {
    private static final String USER_AGENT = "MinecraftAuth/5.0.0";
    private static final String ENTITLEMENTS_URL = "https://api.minecraftservices.com/entitlements/mcstore";

    private static final HttpClient AUTH_CLIENT = MinecraftAuth.createHttpClient(USER_AGENT);
    private static final java.net.http.HttpClient HTTP = java.net.http.HttpClient.newHttpClient();

    static final File tokenFile = LauncherDirs.TOKENS.resolve("tokens.json").toFile();

    private static JavaAuthManager authManager;

    /**
     * Prevent instantiation
     */
    public Login() {
        throw new UnsupportedOperationException(
                "Cannot be instantiated."
        );
    }

    /**
     * Check whether an existing login token already exists and can be used.
     * @return a boolean value of whether there is an existing login (valid token) already.
     */
    public static boolean existingLogin() {
        if (authManager == null) authManager = loadSavedLogin();
        return authManager != null;
    }

    /**
     * Adds a login button and attempts a login
     * @return a boolean on whether the login was successful
     */
    public static boolean login() {
        AtomicReference<JButton> loginButton = new AtomicReference<>();

        try {
            authManager = JavaAuthManager.create(AUTH_CLIENT).login(
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

        saveLogin(authManager);
        return true;
    }

    /**
     * Gets a Minecraft token if there is an existing login
     * @return the Minecraft token as a {@link String} if it exists, {@code null} otherwise
     */
    public static String getMcToken() {
        if (!existingLogin()) return null;

        try {
            String token = authManager.getMinecraftToken().getUpToDate().getToken();
            saveLogin(authManager); // persist refreshed tokens
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
        String token = getMcToken();
        if (token == null) return null;

        try {
            MinecraftProfile profile = authManager.getMinecraftProfile().getUpToDate();
            return new Session(profile.getName(), profile.getId().toString().replace("-", ""), token);
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
        String token = existingLogin() ? getMcToken() : null;
        if (token == null && login()) token = getMcToken();
        if (token == null) return false;

        try {
            if (ownsMinecraft(token)) return true;
            logError("Couldn't verify game ownership!", null);
        } catch (IOException e) {
            logError("Failed to check game ownership.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logError("Ownership check was interrupted.", e);
        }
        return false;
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

    /**
     * Attempts to load an existing saved login using the tokens file
     * @return a {@link JavaAuthManager} for the saved login, or {@code null} if it failed
     */
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

    /**
     * Save login tokens to file
     * @param manager a valid {@link JavaAuthManager} to save the token of
     */
    private static void saveLogin(JavaAuthManager manager) {
        try (Writer writer = Files.newBufferedWriter(tokenFile.toPath())) {
            writer.write(JavaAuthManager.toJson(manager).toString());
        } catch (IOException e) {
            logError("Failed to save login tokens.", e);
        }
    }

}