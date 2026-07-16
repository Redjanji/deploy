package com.xss.imageservice.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ImageConverterTest {

    private byte[] createTestPngData() throws IOException {
        BufferedImage image = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    @Test
    void readImage_validData_returnsBufferedImage() throws IOException {
        byte[] pngData = createTestPngData();

        BufferedImage result = ImageConverter.readImage(pngData);

        assertNotNull(result);
        assertEquals(100, result.getWidth());
        assertEquals(80, result.getHeight());
    }

    @Test
    void readImage_invalidData_returnsNull() throws IOException {
        byte[] invalidData = new byte[]{0x00, 0x01, 0x02, 0x03};

        BufferedImage result = ImageConverter.readImage(invalidData);

        assertNull(result);
    }

    @Test
    void toWebP_validImage_returnsWebPBytes() throws IOException {
        BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);

        byte[] result = ImageConverter.toWebP(image, 0.8f);

        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    void resizeAndConvert_validImage_resizesAndConverts() throws IOException {
        byte[] pngData = createTestPngData();

        byte[] result = ImageConverter.resizeAndConvert(pngData, 50, 50, 0.8f);

        assertNotNull(result);
        assertTrue(result.length > 0);
    }
}
