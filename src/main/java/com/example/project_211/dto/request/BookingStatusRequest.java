package com.example.project_211.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class BookingStatusRequest {
    // Gia tri hop le la CONFIRMED hoac REJECTED
    @NotBlank(message = "Trạng thái không được để trống")
    private String status;
}
