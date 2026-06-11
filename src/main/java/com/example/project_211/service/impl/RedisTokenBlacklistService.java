package com.example.project_211.service.impl;
import com.example.project_211.service.TokenBlacklistService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;   // Spring Boot tu cap bean nay

    private static final String PREFIX = "blacklist:";

    @Override
    public void blacklist(String token, long ttlMillis) {
        redisTemplate.opsForValue().set(
                PREFIX + token,
                "revoked",
                Duration.ofMillis(ttlMillis));    // TTL tu dong het han
    }

    @Override
    public boolean isBlacklisted(String token) {
        // hasKey: O(1) - cuc nhanh, khong dung den MySQL
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + token));
    }
}