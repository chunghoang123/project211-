package com.example.project_211.service.impl;
import com.example.project_211.service.UserService;

import com.example.project_211.dto.request.UserRequest;
import com.example.project_211.dto.response.PageResponse;
import com.example.project_211.dto.response.UserResponse;
import com.example.project_211.entity.Role;
import com.example.project_211.entity.User;
import com.example.project_211.enums.RoleName;
import com.example.project_211.exception.ResourceNotFoundException;
import com.example.project_211.repository.RoleRepository;
import com.example.project_211.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;


    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsers(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());

        Page<User> userPage = (keyword == null || keyword.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                keyword, keyword, pageable);

        List<UserResponse> content = userPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResponse.<UserResponse>builder()
                .content(content)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy người dùng có mã: " + id));
        return toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy người dùng có mã: " + id));

        // Chuyen chuoi vai tro thanh enum, sai thi bao loi tieng Viet
        RoleName roleName;
        try {
            roleName = RoleName.valueOf(request.getRole());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Vai trò không hợp lệ: " + request.getRole());
        }

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò"));

        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setRole(role);
        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }

        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy người dùng có mã: " + id);
        }
        userRepository.deleteById(id);
    }

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
