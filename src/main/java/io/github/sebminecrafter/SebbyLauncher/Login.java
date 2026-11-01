package io.github.sebminecrafter.SebbyLauncher;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;

import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import javax.swing.*;

import static io.github.sebminecrafter.SebbyLauncher.GuiHelpers.open;
import static io.github.sebminecrafter.SebbyLauncher.Main.logger;
import static io.github.sebminecrafter.SebbyLauncher.Window.addItem;


public class Login {
    static File tokenFile = new File("tokens.json");
    static String code;

    private static void addLoginButton(String code) {
        GridBagConstraints loginConstraints = new GridBagConstraints();
        loginConstraints.gridx = 0; // same column as other buttons
        loginConstraints.gridy = 2; // below Play button
        loginConstraints.anchor = GridBagConstraints.CENTER;
        loginConstraints.fill = GridBagConstraints.HORIZONTAL;
        loginConstraints.weightx = 1;
        loginConstraints.weighty = 0;
        loginConstraints.insets = new Insets(10, 0, 0, 0);

        SwingUtilities.invokeLater(() -> {
            JButton loginBtn = new JButton("Login");
            loginBtn.setToolTipText(code);
            loginBtn.addActionListener(_ -> open(URI.create(code)));

            addItem(loginBtn, loginConstraints);
        });


        logger.debug("Button added?", "SebbyLauncher/Login");
    }
    public static boolean existingLogin() {
        try (FileReader reader = new FileReader(tokenFile)) {
            String userAgent = "MinecraftAuth/5.0.0";
            HttpClient httpClient = MinecraftAuth.createHttpClient(userAgent);

            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            JavaAuthManager authManager = JavaAuthManager.fromJson(httpClient, jsonObject);

            return true;
        } catch (IOException | IllegalStateException e) {
            logger.error("No existing credentials or error accessing file.","SebbyLauncher/Login");
            return false;
        }
    }
    public static void login() {
        String userAgent = "MinecraftAuth/5.0.0";
        HttpClient httpClient = MinecraftAuth.createHttpClient(userAgent);
        JavaAuthManager.Builder authManagerBuilder = JavaAuthManager.create(httpClient);
        JavaAuthManager authManager;
        try {
            authManager = authManagerBuilder.login(DeviceCodeMsaAuthService::new, (Consumer<MsaDeviceCode>) deviceCode -> {
                code = deviceCode.getDirectVerificationUri();
                logger.info("Login code: " + code, "SebbyLauncher/Login");
            });
        } catch (IOException | InterruptedException | TimeoutException e) {
            logger.error("Failed to generate login code! Stack trace:","SebbyLauncher/Login");
            logger.trace(Arrays.toString(e.getStackTrace()),"SebbyLauncher/Login");
            return;
        }
        addLoginButton(code);
        try {
            logger.debug("Username: " + authManager.getMinecraftProfile().getUpToDate().getName(),"SebbyLauncher/Login");
        } catch (IOException e) {
            logger.error("Failed to obtain username. Stack trace:","SebbyLauncher/Login");
            logger.trace(Arrays.toString(e.getStackTrace()),"SebbyLauncher/Login");
        }
        try {
            logger.debug("Access token: " + authManager.getMinecraftToken().getUpToDate().getToken(), "SebbyLauncher/Login");
        } catch (IOException e) {
            logger.error("Failed to get access token. Stack trace:","SebbyLauncher/Login");
            logger.trace(Arrays.toString(e.getStackTrace()),"SebbyLauncher/Login");
        }
        try (FileWriter file = new FileWriter(tokenFile)) {
            file.write(JavaAuthManager.toJson(authManager).toString());
            logger.info("JSON file saved successfully.","SebbyLauncher/Login");
        } catch (IOException e) {
            logger.error("Failed to save JSON login. Stack trace:","SebbyLauncher/Login");
            logger.trace(Arrays.toString(e.getStackTrace()),"SebbyLauncher/Login");
        }
    }

    public static boolean ownsMinecraft(String minecraftAccessToken) throws Exception {
        HttpResponse<String> response;
        try (java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient()) {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.minecraftservices.com/entitlements/mcstore"))
                    .header("Authorization", "Bearer " + minecraftAccessToken)
                    .GET()
                    .build();

            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        if (response.statusCode() != 200) {
            return false; // invalid token, expired token, or auth failure
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray items = json.getAsJsonArray("items");

        if (items == null || items.isEmpty()) {
            return false;
        }

        // Optional: explicitly check for Minecraft products
        return items.asList().stream().anyMatch(e ->
                e.getAsJsonObject().get("name").getAsString().toLowerCase().contains("minecraft")
        );
    }
    public static String getMcToken() {
        String token = null;
        JavaAuthManager authManager;
        try (FileReader reader = new FileReader(tokenFile)) {
            String userAgent = "MinecraftAuth/5.0.0";
            HttpClient httpClient = MinecraftAuth.createHttpClient(userAgent);

            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            authManager = JavaAuthManager.fromJson(httpClient, jsonObject);
        } catch (IOException | IllegalStateException e) {
            logger.error("No existing credentials or error accessing file.", "SebbyLauncher/Login");
            return null;
        }
        try {
            token = authManager.getMinecraftToken().getUpToDate().getToken();
        } catch (IOException e) {
            logger.error("SebbyLauncher/Login", "Error getting MC token, stack trace:");
            logger.trace("SebbyLauncher/Login", Arrays.toString(e.getStackTrace()));
        }
        return token;
    }

    public static void attemptLogin() {
        if (!existingLogin()) login();
        try {
            if (!ownsMinecraft(getMcToken())) {
                logger.error("Couldn't verify game ownership!", "SebbyLauncher/Login");
                System.exit(0);
            }
        } catch (Exception e) {
            logger.error(e.toString(), "SebbyLauncher/Login");
        }
    }
}