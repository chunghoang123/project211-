package com.example.project_211.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ForgotPasswordRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Email invalid format")
    private String email;
}