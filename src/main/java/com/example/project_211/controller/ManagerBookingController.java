package com.example.project_211.controller;

import com.example.project_211.dto.request.BookingStatusRequest;
import com.example.project_211.dto.response.ApiResponse;
import com.example.project_211.dto.response.BookingResponse;
import com.example.project_211.dto.response.PageResponse;
import com.example.project_211.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Quan ly duyet va tu choi don dat san, danh cho MANAGER va ADMIN
@RestController
@RequestMapping("/api/v1/manager/bookings")
@RequiredArgsConstructor
public class ManagerBookingController {

    private final BookingService bookingService;

    // Xem danh sach dat san, co the loc theo trang thai
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> getBookings(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đặt sân thành công",
                bookingService.getBookings(status, page, size)));
    }

    // Phe duyet hoac tu choi mot don dat san
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<BookingResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody BookingStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái đặt sân thành công",
                bookingService.updateStatus(id, request.getStatus())));
    }
}
