package io.github.sebminecrafter.sebbylauncher.window;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectIconImageReaderTest {
    @Test
    void webpImageReaderIsAvailableForModrinthIcons() {
        assertTrue(ImageIO.getImageReadersByFormatName("webp").hasNext());
    }
}
