package com.example.project_211.security;

import com.example.project_211.dto.response.ErrorResponse;
import com.example.project_211.service.TokenBlacklistService;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@RequiredArgsConstructor
public class JwtRequestFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Khong co header Bearer -> cho di tiep (endpoint public van chay,
        //    endpoint can quyen se bi EntryPoint tra 401 sau)
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        // 2. Token sai chu ky / het han -> khong set context -> 401 o EntryPoint
        if (!jwtUtil.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. UC-03: token nam trong Blacklist -> CHAN NGAY, tra 403
        //    (ke ca token chua het han ve mat thoi gian)
        if (tokenBlacklistService.isBlacklisted(token)) {
            writeError(response, request, "Token đã bị thu hồi");
            return;
        }

        // 4. Token sach -> nap user vao SecurityContext de Spring check role
        String username = jwtUtil.extractUsername(token);
        if (username != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, HttpServletRequest request,
                            String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);   // 403 theo SRS
        response.setContentType("application/json;charset=UTF-8");
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(403)
                .error("Không có quyền truy cập")
                .message(message)
                .path(request.getRequestURI())
                .build();
        objectMapper.writeValue(response.getWriter(), body);
    }
}
