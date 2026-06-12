package com.example.project_211.service.impl;
import com.example.project_211.service.TokenBlacklistService;

import com.example.project_211.dto.request.RegisterRequest;
import com.example.project_211.dto.response.UserResponse;
import com.example.project_211.entity.Role;
import com.example.project_211.entity.User;
import com.example.project_211.enums.RoleName;
import com.example.project_211.exception.DuplicateResourceException;
import com.example.project_211.repository.RefreshTokenRepository;
import com.example.project_211.repository.RoleRepository;
import com.example.project_211.repository.UserRepository;
import com.example.project_211.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)        // bat Mockito, KHONG khoi dong Spring
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil jwtUtil;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private TokenBlacklistService tokenBlacklistService;

    @InjectMocks                            // tu tiem cac @Mock vao constructor
    private AuthServiceImpl authService;

    private RegisterRequest request;

    @BeforeEach
    void setUp() {
        request = new RegisterRequest();
        request.setUsername("customer1");
        request.setPassword("123456");
        request.setEmail("customer1@gmail.com");
        request.setFullName("Nguyen Van A");
    }

    @Test
    @DisplayName("TEST 1 - Dang ky thanh cong: ma hoa BCrypt, gan ROLE_CUSTOMER")
    void register_shouldSucceed_whenDataIsValid() {
        // GIVEN: gia lap moi truong
        Role customerRole = Role.builder().id(3L).name(RoleName.ROLE_CUSTOMER).build();
        when(userRepository.existsByUsername("customer1")).thenReturn(false);
        when(userRepository.existsByEmail("customer1@gmail.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.ROLE_CUSTOMER))
                .thenReturn(Optional.of(customerRole));
        when(passwordEncoder.encode("123456")).thenReturn("$2a$10$hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        // WHEN: goi method can test
        UserResponse result = authService.register(request);

        // THEN: kiem tra ket qua
        assertThat(result.getUsername()).isEqualTo("customer1");
        assertThat(result.getRole()).isEqualTo("ROLE_CUSTOMER");
        verify(passwordEncoder).encode("123456");      // chac chan co ma hoa BCrypt
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("TEST 2 - Dang ky trung username: nem DuplicateResourceException (409)")
    void register_shouldThrow_whenUsernameExists() {
        when(userRepository.existsByUsername("customer1")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Tên đăng nhập đã tồn tại");

        // Quan trong: KHONG duoc luu gi xuong DB
        verify(userRepository, never()).save(any());
    }
}