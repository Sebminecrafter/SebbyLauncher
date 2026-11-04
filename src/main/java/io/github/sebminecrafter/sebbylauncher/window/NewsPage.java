package io.github.sebminecrafter.sebbylauncher.window;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.net.URI;

import static io.github.sebminecrafter.sebbylauncher.GuiHelpers.open;
import static io.github.sebminecrafter.sebbylauncher.window.Window.*;

public final class NewsPage {
    private static final String HOME_URL = "https://www.minecraft.net/article";
    private static WebEngine newsEngine;

    private NewsPage() {}

    static JComponent create() {
        JPanel screen = new JPanel(new BorderLayout(8, 8));
        screen.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.add(pageTitle("News"), BorderLayout.NORTH);
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton home = new JButton("Home");
        home.addActionListener(_ -> loadNews());
        JButton copy = new JButton("Copy URL");
        copy.addActionListener(_ -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(currentNewsUrl), null);
            newsStatus.setText("Copied " + currentNewsUrl);
        });
        JButton browser = new JButton("Open in browser");
        browser.addActionListener(_ -> open(URI.create(currentNewsUrl)));
        toolbar.add(home);
        toolbar.add(copy);
        toolbar.add(browser);
        header.add(toolbar, BorderLayout.SOUTH);
        screen.add(header, BorderLayout.NORTH);

        JFXPanel webViewPanel = new JFXPanel();
        screen.add(webViewPanel, BorderLayout.CENTER);
        newsStatus = new JLabel("Loading Minecraft news…");
        screen.add(newsStatus, BorderLayout.SOUTH);

        Platform.runLater(() -> {
            WebView webView = new WebView();
            newsEngine = webView.getEngine();
            newsEngine.getLoadWorker().stateProperty().addListener((_, _, state) -> {
                if (state == Worker.State.SCHEDULED || state == Worker.State.RUNNING) {
                    SwingUtilities.invokeLater(() -> newsStatus.setText("Loading " + newsEngine.getLocation() + "…"));
                } else if (state == Worker.State.SUCCEEDED) {
                    String location = newsEngine.getLocation();
                    SwingUtilities.invokeLater(() -> {
                        if (isWebUrl(location)) currentNewsUrl = location;
                        newsStatus.setText(currentNewsUrl);
                    });
                } else if (state == Worker.State.FAILED) {
                    Throwable error = newsEngine.getLoadWorker().getException();
                    logger.error("Failed to load Minecraft news: "
                            + (error == null ? "unknown WebView error" : error.getMessage()));
                    SwingUtilities.invokeLater(() -> newsStatus.setText("Unable to load " + currentNewsUrl));
                }
            });
            webViewPanel.setScene(new Scene(webView));
            newsEngine.load(currentNewsUrl);
        });
        return screen;
    }

    private static void loadNews() {
        if (!isWebUrl(NewsPage.HOME_URL)) {
            JOptionPane.showMessageDialog(frame, "Only web links can be opened in the news viewer.",
                    "Invalid news URL", JOptionPane.ERROR_MESSAGE);
            return;
        }

        currentNewsUrl = NewsPage.HOME_URL;
        newsStatus.setText("Loading " + NewsPage.HOME_URL + "…");
        Platform.runLater(() -> {
            if (newsEngine != null) newsEngine.load(NewsPage.HOME_URL);
        });
    }

    static boolean isWebUrl(String url) {
        try {
            URI uri = URI.create(url);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

}
