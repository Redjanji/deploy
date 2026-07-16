package com.xss.imageservice.service.impl;

import com.xss.imageservice.service.StorageService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;
    private final String bucket;

    @Override
    public String upload(byte[] data, String appId, String sizeType, String extension) throws IOException {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String uuid = UUID.randomUUID().toString();
        String appPath = (appId != null && !appId.isEmpty()) ? appId : "default";
        String objectName = String.format("%s/%s/%s/%s.%s", appPath, date, sizeType, uuid, extension);

        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType("image/webp")
                    .build());
        } catch (Exception e) {
            throw new IOException("上传文件到 MinIO 失败", e);
        }
        return objectName;
    }

    @Override
    public void delete(String objectKey) throws IOException {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new IOException("从 MinIO 删除文件失败", e);
        }
    }
}
