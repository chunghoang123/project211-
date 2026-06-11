package com.example.project_211.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class BookingStatusRequest {
    @NotBlank(message = "status is required")
    private String status;        // "CONFIRMED" hoac "REJECTED"
}