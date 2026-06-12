package com.example.project_211.controller;

import com.example.project_211.dto.request.RegisterRequest;
import com.example.project_211.dto.response.UserResponse;
import com.example.project_211.service.AuthService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)               // chi load tang web cua 1 controller
@AutoConfigureMockMvc(addFilters = false)       // tat security filter trong test
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;          // gia lap HTTP request
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean                                 // Boot < 3.4 thi doi thanh @MockBean
    private AuthService authService;

    @Test
    @DisplayName("TEST 6 - POST /register hop le: tra 201 + format ApiResponse")
    void register_shouldReturn201_whenValid() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("customer1");
        request.setPassword("123456");
        request.setEmail("customer1@gmail.com");

        UserResponse response = UserResponse.builder()
                .id(1L).username("customer1")
                .email("customer1@gmail.com").role("ROLE_CUSTOMER").build();
        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())                       // 201 (SRS)
                .andExpect(jsonPath("$.success").value(true))          // format SRS VI.3
                .andExpect(jsonPath("$.data.username").value("customer1"));
    }

    @Test
    @DisplayName("TEST 7 - Email sai dinh dang: tra 400 + format ErrorResponse")
    void register_shouldReturn400_whenEmailInvalid() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("customer1");
        request.setPassword("123456");
        request.setEmail("not-an-email");          // sai format

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())                    // 400 (SRS)
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"));
    }
}