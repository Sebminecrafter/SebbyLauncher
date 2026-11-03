package io.github.sebminecrafter.sebbylauncher.window;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountsPageTest {
    @Test
    void secureImageUriUpgradesMinecraftTextureUrlsToHttps() throws IOException {
        assertEquals(
                URI.create("https://textures.minecraft.net/texture/example"),
                AccountsPage.getSecureImageUri("http://textures.minecraft.net/texture/example"));
    }

    @Test
    void secureImageUriPreservesHttpsUrls() throws IOException {
        URI url = URI.create("https://example.com/image.png");

        assertEquals(url, AccountsPage.getSecureImageUri(url.toString()));
    }

    @Test
    void secureImageUriRejectsHttpUrlsOutsideMinecraftTextureHost() {
        assertThrows(
                IOException.class,
                () -> AccountsPage.getSecureImageUri("http://example.com/image.png"));
    }
}
