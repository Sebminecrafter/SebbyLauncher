package io.github.sebminecrafter.sebbylauncher.window;

import io.github.sebminecrafter.sebbylauncher.LauncherDetails;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static io.github.sebminecrafter.sebbylauncher.GuiHelpers.*;
import static io.github.sebminecrafter.sebbylauncher.window.Window.*;

public final class NewsPage {
    private NewsPage() {}

    static JComponent create() {
        JPanel screen = new JPanel(new BorderLayout(8, 8));
        screen.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton home = new JButton("Home");
        home.addActionListener(_ -> loadNews("https://www.minecraft.net/article"));
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
        screen.add(toolbar, BorderLayout.NORTH);

        newsPane = new JEditorPane();
        newsPane.setEditable(false);
        newsPane.setContentType("text/html");
        newsPane.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getURL() != null) {
                loadNews(event.getURL().toString());
            }
        });
        screen.add(new JScrollPane(newsPane), BorderLayout.CENTER);
        newsStatus = new JLabel("Loading Minecraft news…");
        screen.add(newsStatus, BorderLayout.SOUTH);
        loadNews(currentNewsUrl);
        return screen;
    }

    private static void loadNews(String requestedUrl) {
        URI uri;
        try {
            uri = URI.create(requestedUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("Only web links can be opened in the news viewer.");
            }
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(frame, e.getMessage(), "Invalid news URL", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int requestId = newsRequest.incrementAndGet();
        newsStatus.setText("Loading " + uri + "…");
        new SwingWorker<NewsArticle, Void>() {
            @Override
            protected NewsArticle doInBackground() throws Exception {
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(java.time.Duration.ofSeconds(25))
                        .header("User-Agent", LauncherDetails.NAME + "/" + LauncherDetails.VERSION)
                        .GET().build();
                HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    try (InputStream body = response.body()) {
                        body.readNBytes(4096);
                    }
                    throw new IOException("News site returned HTTP " + response.statusCode() + ".");
                }
                byte[] html;
                try (InputStream body = response.body()) {
                    html = body.readNBytes(4_000_001);
                }
                if (html.length > 4_000_000) throw new IOException("News page is larger than 4 MB.");
                return new NewsArticle(response.uri().toString(), new String(html, StandardCharsets.UTF_8));
            }

            @Override
            protected void done() {
                if (requestId != newsRequest.get()) return;
                try {
                    NewsArticle page = get();
                    currentNewsUrl = page.url();
                    newsPane.setText(addNewsBaseUrl(page.html(), page.url()));
                    newsPane.setCaretPosition(0);
                    newsStatus.setText(currentNewsUrl);
                } catch (Exception e) {
                    newsPane.setText("<html><body><h2>News page could not be loaded</h2><p>"
                            + PlayPage.escapeHtml(e.getMessage())
                            + "</p><p>Use “Open in browser” to view the page.</p></body></html>");
                    newsStatus.setText("Unable to load " + uri);
                    logger.error("Failed to load Minecraft news: " + e.getMessage());
                }
            }
        }.execute();
    }

    private static String addNewsBaseUrl(String html, String url) {
        int headStart = html.toLowerCase(java.util.Locale.ROOT).indexOf("<head");
        int headEnd = headStart < 0 ? -1 : html.indexOf('>', headStart);
        String base = "<base href=\"" + url.replace("&", "&amp;").replace("\"", "&quot;") + "\">";
        if (headEnd >= 0) return html.substring(0, headEnd + 1) + base + html.substring(headEnd + 1);
        return "<html><head>" + base + "</head><body>" + html + "</body></html>";
    }

    private record NewsArticle(String url, String html) {}
}
