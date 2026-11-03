package io.github.sebminecrafter.sebbylauncher.window;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewsPageTest {
    @Test
    void acceptsHttpAndHttpsNewsUrls() {
        assertTrue(NewsPage.isWebUrl("https://www.minecraft.net/article"));
        assertTrue(NewsPage.isWebUrl("http://example.com/news"));
    }

    @Test
    void rejectsNonWebOrMalformedNewsUrls() {
        assertFalse(NewsPage.isWebUrl("file:///C:/private.html"));
        assertFalse(NewsPage.isWebUrl("javascript:alert(1)"));
        assertFalse(NewsPage.isWebUrl("not a url"));
    }

}
