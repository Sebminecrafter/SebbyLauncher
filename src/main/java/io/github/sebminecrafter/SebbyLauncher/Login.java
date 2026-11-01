package io.github.sebminecrafter.SebbyLauncher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
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

import static io.github.sebminecrafter.SebbyLauncher.GuiHelpers.open;
import static io.github.sebminecrafter.SebbyLauncher.Main.logger;
import static io.github.sebminecrafter.SebbyLauncher.Window.addItem;

public class Login {
    private static final String TAG = "SebbyLauncher/Login";
    private static final String USER_AGENT = "MinecraftAuth/5.0.0";
    private static final String ENTITLEMENTS_URL = "https://api.minecraftservices.com/entitlements/mcstore";

    private static final HttpClient AUTH_CLIENT = MinecraftAuth.createHttpClient(USER_AGENT);
    private static final java.net.http.HttpClient HTTP = java.net.http.HttpClient.newHttpClient();

    static final File tokenFile = new File("tokens.json");

    public static boolean existingLogin() {
        if (authManager == null) authManager = loadSavedLogin();
        return authManager != null;
    }

    public static boolean login() {
        AtomicReference<JButton> loginButton = new AtomicReference<>();

        try {
            authManager = JavaAuthManager.create(AUTH_CLIENT).login(
                    DeviceCodeMsaAuthService::new,
                    (Consumer<MsaDeviceCode>) deviceCode -> {
                        String url = deviceCode.getDirectVerificationUri();
                        logger.info("Login URL: " + url, TAG);
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

    public static boolean attemptLogin() {
        String token = existingLogin() ? getMcToken() : null;
        if (token == null && login()) token = getMcToken();
        if (token == null) return false;

        try {
            if (ownsMinecraft(token)) return true;
            logger.error("Couldn't verify game ownership!", TAG);
        } catch (IOException e) {
            logError("Failed to check game ownership.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logError("Ownership check was interrupted.", e);
        }
        return false;
    }    private static JavaAuthManager authManager;

    private static void logError(String message, Exception e) {
        logger.error(message, TAG);
        logger.trace(Arrays.toString(e.getStackTrace()), TAG);
    }

    private static JButton showLoginButton(String url) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 2;
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

    private static void saveLogin(JavaAuthManager manager) {
        try (Writer writer = Files.newBufferedWriter(tokenFile.toPath())) {
            writer.write(JavaAuthManager.toJson(manager).toString());
        } catch (IOException e) {
            logError("Failed to save login tokens.", e);
        }
    }

}