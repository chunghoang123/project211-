package com.example.project_211.service.impl;

import com.example.project_211.exception.ResourceNotFoundException;
import com.example.project_211.repository.RoleRepository;
import com.example.project_211.repository.UserRepository;
import com.example.project_211.service.impl.UserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    @DisplayName("TEST 5 - Tim user khong ton tai: nem ResourceNotFoundException (404)")
    void getUserById_shouldThrow_whenUserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }
}