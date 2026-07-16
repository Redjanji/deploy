package com.xss.imageservice.security;

import com.xss.imageservice.config.ImageConfigProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ImageSecurityChecker {

    private final ImageConfigProperties config;

    public void check(MultipartFile file) throws IOException {
        String ext = FilenameUtils.getExtension(file.getOriginalFilename()).toLowerCase();
        if (!config.getAllowedExtensions().contains(ext)) {
            throw new SecurityException("不支持的文件后缀: " + ext);
        }

        if (file.getSize() > config.getMaxSize()) {
            throw new SecurityException("文件过大");
        }

        byte[] data = file.getBytes();

        Tika tika = new Tika();
        String mime = tika.detect(data);
        if (!config.getAllowedMimeTypes().contains(mime)) {
            throw new SecurityException("文件内容非图片，检测为: " + mime);
        }

        if (!MagicNumber.isImage(data)) {
            throw new SecurityException("文件头魔数不匹配");
        }

        try (ByteArrayInputStream is = new ByteArrayInputStream(data)) {
            BufferedImage image = ImageIO.read(is);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new SecurityException("无法解析为有效图片");
            }
        }
    }
}
