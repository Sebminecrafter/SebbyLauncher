package io.github.sebminecrafter.sebbylauncher.window;

import io.github.sebminecrafter.sebbylauncher.Login;
import io.github.sebminecrafter.sebbylauncher.Session;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.sebminecrafter.sebbylauncher.GuiHelpers.*;
import static io.github.sebminecrafter.sebbylauncher.window.Window.*;

public final class AccountsPage {
    private AccountsPage() {}

    static JComponent create() {
        JPanel screen = new JPanel(new BorderLayout(16, 16));
        screen.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JLabel title = new JLabel("Accounts");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));

        JPanel accountSidebar = new JPanel(new BorderLayout(8, 8));
        accountSidebar.setPreferredSize(new Dimension(240, 0));
        accountList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        accountList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                    boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof Login.Account account) {
                    label.setText(account.toString());
                    label.setIcon(accountHeads.get(account.uuid()));
                }
                label.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
                return label;
            }
        });
        accountList.addListSelectionListener(_ -> {
            if (!refreshingAccounts && !accountList.getValueIsAdjusting()) {
                Login.Account selected = accountList.getSelectedValue();
                if (selected != null) {
                    Login.setSelectedAccount(selected.id());
                    PlayPage.refreshAccountSelector();
                    refreshAccountDetails(selected);
                }
            }
        });
        accountSidebar.add(new JScrollPane(accountList), BorderLayout.CENTER);

        JPanel accountActions = new JPanel(new GridLayout(0, 1, 0, 6));
        JButton offlineButton = new JButton("Add offline account");
        offlineButton.addActionListener(_ -> addOfflineAccount());
        accountActions.add(offlineButton);
        JButton microsoftButton = new JButton("Add Microsoft account");
        microsoftButton.addActionListener(_ -> addMicrosoftAccount());
        accountActions.add(microsoftButton);
        JButton removeButton = new JButton("Remove selected");
        removeButton.addActionListener(_ -> {
            Login.Account selected = accountList.getSelectedValue();
            if (selected != null && JOptionPane.showConfirmDialog(frame,
                    "Remove " + selected.name() + " from this launcher?", "Remove account",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                Login.removeAccount(selected.id());
                PlayPage.refreshAccountSelector();
                refreshAccountPage();
            }
        });
        accountActions.add(removeButton);
        accountSidebar.add(accountActions, BorderLayout.SOUTH);

        JPanel profile = new JPanel(new BorderLayout(12, 12));
        JPanel profileHeader = new JPanel();
        profileHeader.setLayout(new BoxLayout(profileHeader, BoxLayout.Y_AXIS));
        accountNameLabel = new JLabel("Select an account");
        accountNameLabel.setFont(accountNameLabel.getFont().deriveFont(Font.BOLD, 20f));
        accountTypeLabel = new JLabel(" ");
        profileHeader.add(accountNameLabel);
        profileHeader.add(accountTypeLabel);
        profile.add(profileHeader, BorderLayout.NORTH);

        JPanel cosmetics = new JPanel(new GridLayout(1, 2, 12, 0));
        JPanel skinPanel = new JPanel(new BorderLayout(6, 6));
        skinPanel.setBorder(BorderFactory.createTitledBorder("Skin"));
        skinImageLabel = new JLabel("Loading profile…", SwingConstants.CENTER);
        skinPanel.add(skinImageLabel, BorderLayout.CENTER);
        JPanel capePanelContainer = new JPanel(new BorderLayout());
        capePanelContainer.setBorder(BorderFactory.createTitledBorder("Owned capes"));
        capeList = new JPanel();
        capeList.setLayout(new BoxLayout(capeList, BoxLayout.Y_AXIS));
        capePanelContainer.add(new JScrollPane(capeList), BorderLayout.CENTER);
        cosmetics.add(skinPanel);
        cosmetics.add(capePanelContainer);
        profile.add(cosmetics, BorderLayout.CENTER);

        screen.add(title, BorderLayout.NORTH);
        screen.add(accountSidebar, BorderLayout.WEST);
        screen.add(profile, BorderLayout.CENTER);
        refreshAccountPage();
        return screen;
    }

    private static void addOfflineAccount() {
        String name = JOptionPane.showInputDialog(frame, "Choose a player name:", "Add Offline Account",
                JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.isBlank()) {
            if (Login.addOfflineAccount(name)) {
                PlayPage.refreshAccountSelector();
                refreshAccountPage();
            } else {
                JOptionPane.showMessageDialog(frame, "Player names may contain only letters, numbers, and underscores.",
                        "Invalid player name", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private static void addMicrosoftAccount() {
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return Login.login();
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        PlayPage.refreshAccountSelector();
                        refreshAccountPage();
                    } else {
                        JOptionPane.showMessageDialog(frame, "Microsoft sign-in did not complete.",
                                "Account sign-in", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception e) {
                    logger.error("Failed to add Microsoft account: " + e.getMessage());
                    JOptionPane.showMessageDialog(frame, e.getMessage(), "Account sign-in failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static void refreshAccountPage() {
        if (accountNameLabel == null) return;
        Login.Account selected = Login.getSelectedAccount();
        refreshingAccounts = true;
        accountListModel.clear();
        for (Login.Account account : Login.getAccounts()) {
            accountListModel.addElement(account);
        }
        if (selected != null) {
            accountList.setSelectedValue(selected, true);
        } else if (!accountListModel.isEmpty()) {
            accountList.setSelectedIndex(0);
        } else {
            accountNameLabel.setText("No accounts added");
            accountTypeLabel.setText("Add an offline or Microsoft account to get started.");
            skinImageLabel.setIcon(null);
            skinImageLabel.setText("No account selected");
            capeList.removeAll();
            capeList.revalidate();
            capeList.repaint();
        }
        refreshingAccounts = false;
        loadAccountHeads();
        if (selected != null) refreshAccountDetails(selected);
    }

    private static void loadAccountHeads() {
        List<Login.Account> accounts = Login.getAccounts();
        new SwingWorker<Map<String, ImageIcon>, Void>() {
            @Override
            protected Map<String, ImageIcon> doInBackground() throws Exception {
                Map<String, ImageIcon> loaded = new HashMap<>();
                for (Login.Account account : accounts) {
                    if (account.uuid() == null || account.uuid().isBlank() || accountHeads.containsKey(account.uuid())) {
                        continue;
                    }
                    URI uri = URI.create("https://crafatar.com/avatars/" + account.uuid() + "?size=64&overlay");
                    HttpRequest request = HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(10)).GET().build();
                    HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    if (response.statusCode() == 200) {
                        try (InputStream body = response.body()) {
                            byte[] bytes = body.readNBytes(2_000_001);
                            if (bytes.length > 2_000_000) throw new IOException("Account head image is larger than 2 MB.");
                            BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                            if (image != null) loaded.put(account.uuid(), new ImageIcon(image));
                        }
                    } else {
                        response.body().close();
                    }
                }
                return loaded;
            }

            @Override
            protected void done() {
                try {
                    accountHeads.putAll(get());
                    accountList.repaint();
                } catch (Exception e) {
                    logger.error("Failed to load account head images: " + e.getMessage());
                }
            }
        }.execute();
    }

    private static void refreshAccountDetails(Login.Account account) {
        accountNameLabel.setText(account.name() == null ? "Unknown account" : account.name());
        accountTypeLabel.setText(account.type() == null ? "" : account.type());
        skinImageLabel.setIcon(null);
        skinImageLabel.setText("Loading skin and capes…");
        capeList.removeAll();
        capeList.add(new JLabel("Loading owned capes…"));
        capeList.revalidate();
        capeList.repaint();

        new SwingWorker<Cosmetics, Void>() {
            @Override
            protected Cosmetics doInBackground() throws Exception {
                if (!"microsoft".equalsIgnoreCase(account.type())) {
                    return new Cosmetics(null, List.of(), "Offline accounts do not have an online skin profile.");
                }
                Login.Account current = Login.getSelectedAccount();
                if (current == null || !account.id().equals(current.id())) {
                    return new Cosmetics(null, List.of(), "");
                }
                Session session = Login.getSession();
                if (session == null) throw new IOException("Could not refresh the selected Minecraft profile.");
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                        .timeout(java.time.Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + session.accessToken())
                        .GET().build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IOException("Minecraft profile request failed (HTTP " + response.statusCode() + ").");
                }
                JsonObject profile = JsonParser.parseString(response.body()).getAsJsonObject();
                String skinUrl = null;
                JsonArray skins = profile.has("skins") && profile.get("skins").isJsonArray()
                        ? profile.getAsJsonArray("skins") : new JsonArray();
                for (JsonElement element : skins) {
                    JsonObject skin = element.getAsJsonObject();
                    if (skinUrl == null) skinUrl = skin.has("url") ? skin.get("url").getAsString() : null;
                    if (skin.has("state") && "ACTIVE".equalsIgnoreCase(skin.get("state").getAsString())) {
                        skinUrl = skin.has("url") ? skin.get("url").getAsString() : skinUrl;
                        break;
                    }
                }
                List<CapeCosmetic> capes = new ArrayList<>();
                JsonArray capeEntries = profile.has("capes") && profile.get("capes").isJsonArray()
                        ? profile.getAsJsonArray("capes") : new JsonArray();
                for (JsonElement element : capeEntries) {
                    JsonObject cape = element.getAsJsonObject();
                    String label = cape.has("alias") ? cape.get("alias").getAsString() : "Minecraft cape";
                    String url = cape.has("url") ? cape.get("url").getAsString() : null;
                    capes.add(new CapeCosmetic(label, downloadImage(url)));
                }
                BufferedImage skinImage = downloadImage(skinUrl);
                return new Cosmetics(skinImage, capes, skinUrl == null ? "No skin is set on this profile." : "");
            }

            @Override
            protected void done() {
                if (!account.equals(Login.getSelectedAccount())) return;
                try {
                    Cosmetics result = get();
                    skinImageLabel.setText(result.message());
                    skinImageLabel.setIcon(result.skin() == null ? null
                            : scaleIcon(new ImageIcon(result.skin()), 160, 220));
                    capeList.removeAll();
                    if (result.capes().isEmpty()) {
                        capeList.add(new JLabel("No capes found."));
                    } else {
                        for (CapeCosmetic cape : result.capes()) {
                            JLabel label = new JLabel(cape.name());
                            if (cape.image() != null) label.setIcon(scaleIcon(new ImageIcon(cape.image()), 64, 32));
                            capeList.add(label);
                        }
                    }
                    capeList.revalidate();
                    capeList.repaint();
                } catch (Exception e) {
                    skinImageLabel.setText("Could not load cosmetics.");
                    capeList.removeAll();
                    capeList.add(new JLabel("Profile cosmetics unavailable."));
                    capeList.revalidate();
                    capeList.repaint();
                    logger.error("Failed to load Minecraft cosmetics: " + e.getMessage());
                }
            }
        }.execute();
    }

    private static BufferedImage downloadImage(String imageUrl) throws IOException, InterruptedException {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        URI uri = getSecureImageUri(imageUrl);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(15)).GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("Image request failed (HTTP " + response.statusCode() + ").");
            }
            byte[] image = body.readNBytes(2_000_001);
            if (image.length > 2_000_000) throw new IOException("Image response is larger than 2 MB.");
            return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(image));
        }
    }

    static URI getSecureImageUri(String imageUrl) throws IOException {
        URI uri = URI.create(imageUrl);
        if ("http".equalsIgnoreCase(uri.getScheme())
                && "textures.minecraft.net".equalsIgnoreCase(uri.getHost())) {
            uri = URI.create("https" + imageUrl.substring(imageUrl.indexOf(':')));
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IOException("Image URL must use HTTPS.");
        return uri;
    }

    private record CapeCosmetic(String name, BufferedImage image) {}
    private record Cosmetics(BufferedImage skin, List<CapeCosmetic> capes, String message) {}
}
