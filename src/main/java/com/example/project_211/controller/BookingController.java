package com.example.project_211.controller;

import com.example.project_211.dto.request.BookingRequest;
import com.example.project_211.dto.response.ApiResponse;
import com.example.project_211.dto.response.BookingResponse;
import com.example.project_211.dto.response.PageResponse;
import com.example.project_211.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Khach hang dat san va xem lich su dat san cua minh
@RestController
@RequestMapping("/api/v1/customer/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    // Dat san, lay ten dang nhap tu token, tao thanh cong tra ve 201
    @PostMapping
    public ResponseEntity<ApiResponse<List<BookingResponse>>> createBooking(
            Authentication authentication,
            @Valid @RequestBody BookingRequest request) {
        String username = authentication.getName();
        List<BookingResponse> data = bookingService.createBooking(username, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đặt sân thành công", data));
    }

    // Xem lich su dat san cua chinh minh, co phan trang
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> getMyBookings(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử đặt sân thành công",
                bookingService.getMyBookings(authentication.getName(), page, size)));
    }
}
