package com.example.project_211.service;

public interface TokenBlacklistService {

    void blacklist(String token, long ttlMillis);

    boolean isBlacklisted(String token);
}