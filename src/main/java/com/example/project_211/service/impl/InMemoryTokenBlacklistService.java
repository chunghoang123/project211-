package com.example.project_211.service.impl;

import com.example.project_211.service.TokenBlacklistService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class InMemoryTokenBlacklistService implements TokenBlacklistService {

    // token -> thoi diem het han (epoch millis)
    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    @Override
    public void blacklist(String token, long ttlMillis) {
        blacklist.put(token, System.currentTimeMillis() + ttlMillis);
    }

    @Override
    public boolean isBlacklisted(String token) {
        Long expiry = blacklist.get(token);
        if (expiry == null) return false;
        if (expiry < System.currentTimeMillis()) {   // het han thi don luon
            blacklist.remove(token);
            return false;
        }
        return true;
    }
}