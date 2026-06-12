package com.example.project_211.controller;

import com.example.project_211.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("TEST 10 - DELETE user: tra 204 No Content, body rong (SRS)")
    void deleteUser_shouldReturn204() throws Exception {
        doNothing().when(userService).deleteUser(5L);

        mockMvc.perform(delete("/api/v1/admin/users/5"))
                .andExpect(status().isNoContent());
    }
}