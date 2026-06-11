package com.example.project_211.security;

import com.example.project_211.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;                    // >= 32 ky tu = 256-bit (SRS)

    @Value("${jwt.access-expiration}")
    private long accessExpiration;            // 1800000ms = 30 phut

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }


    public String generateAccessToken(User user) {
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole().getName().name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())   // verify chu ky - sai key la nem JwtException
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return parse(token).getSubject();
    }


    public long getRemainingTime(String token) {
        return parse(token).getExpiration().getTime() - System.currentTimeMillis();
    }

    public boolean isTokenValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;   // sai chu ky, het han, token rac... deu vao day
        }
    }
}