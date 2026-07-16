package com.xss.imageservice.security;

import com.xss.imageservice.config.ImageConfigProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageSecurityCheckerTest {

    @Mock
    private ImageConfigProperties config;

    @InjectMocks
    private ImageSecurityChecker securityChecker;

    private byte[] validPngBytes;

    @BeforeEach
    void setUp() throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        validPngBytes = baos.toByteArray();

        lenient().when(config.getAllowedExtensions()).thenReturn(Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp"));
        lenient().when(config.getMaxSize()).thenReturn(10485760L);
        lenient().when(config.getAllowedMimeTypes()).thenReturn(Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"));
    }

    @Test
    void check_validPngImage_passes() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.png");
        when(file.getSize()).thenReturn((long) validPngBytes.length);
        when(file.getBytes()).thenReturn(validPngBytes);

        assertDoesNotThrow(() -> securityChecker.check(file));
    }

    @Test
    void check_unsupportedExtension_throwsException() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.txt");

        SecurityException exception = assertThrows(SecurityException.class,
                () -> securityChecker.check(file));

        assertTrue(exception.getMessage().contains("不支持的文件后缀"));
    }

    @Test
    void check_fileTooLarge_throwsException() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.png");
        when(file.getSize()).thenReturn(20485760L);

        SecurityException exception = assertThrows(SecurityException.class,
                () -> securityChecker.check(file));

        assertEquals("文件过大", exception.getMessage());
    }

    @Test
    void check_mimeTypeMismatch_throwsException() throws IOException {
        byte[] fakeData = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
                0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F};

        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.png");
        when(file.getSize()).thenReturn((long) fakeData.length);
        when(file.getBytes()).thenReturn(fakeData);

        assertThrows(SecurityException.class, () -> securityChecker.check(file));
    }
}
