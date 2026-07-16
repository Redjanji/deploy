package com.xss.imageservice.service;

import java.io.IOException;

public interface StorageService {
    String upload(byte[] data, String appId, String sizeType, String extension) throws IOException;
    void delete(String objectKey) throws IOException;
}
