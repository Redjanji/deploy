package com.xss.authservice.service;

public interface TokenBlacklistService {

    void addToBlacklist(String token);

    boolean isBlacklisted(String token);
}
