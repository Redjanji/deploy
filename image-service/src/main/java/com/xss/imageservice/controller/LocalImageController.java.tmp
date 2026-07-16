package com.xss.imageservice.controller;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class LocalImageController {

    private final MinioClient minioClient;

    @Value("${minio.bucket:images}")
    private String bucket;

    @GetMapping("/{appId}/{date}/{sizeType}/{filename}")
    public ResponseEntity<byte[]> getImage(@PathVariable String appId,
                                           @PathVariable String date,
                                           @PathVariable String sizeType,
                                           @PathVariable String filename) {
        try {
            String objectKey = String.format("%s/%s/%s/%s", appId, date, sizeType, filename);
            InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = stream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            stream.close();

            byte[] data = baos.toByteArray();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("image/webp"));
            headers.setContentLength(data.length);
            return ResponseEntity.ok().headers(headers).body(data);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
