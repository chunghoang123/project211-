package com.example.project_211.service.impl;

import com.example.project_211.dto.request.RegisterRequest;
import com.example.project_211.dto.response.UserResponse;
import com.example.project_211.entity.Role;
import com.example.project_211.entity.User;
import com.example.project_211.enums.RoleName;
import com.example.project_211.exception.DuplicateResourceException;
import com.example.project_211.exception.ResourceNotFoundException;
import com.example.project_211.repository.RoleRepository;
import com.example.project_211.repository.UserRepository;
import com.example.project_211.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor   // Lombok tu sinh constructor -> Spring tu inject (DI chuan)
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        // 1. Check trung -> nem exception -> GlobalExceptionHandler tra 409
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Tên đăng nhập đã tồn tại");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email đã tồn tại");
        }

        // 2. Tai khoan tu dang ky mac dinh la CUSTOMER
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò"));

        // 3. Ma hoa BCrypt - SRS cam luu plain text
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(customerRole)
                .build();

        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    // Map Entity -> DTO (dung chung)
    private UserResponse toResponse(User u) {
        return UserResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .phone(u.getPhone())
                .role(u.getRole().getName().name())
                .active(u.isActive())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
