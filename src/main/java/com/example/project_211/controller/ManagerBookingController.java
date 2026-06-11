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

@RestController
@RequestMapping("/api/v1/manager/bookings")   // MANAGER + ADMIN (theo FR-08)
@RequiredArgsConstructor
public class ManagerBookingController {

    private final BookingService bookingService;

    // GET /api/v1/manager/bookings?status=PENDING&page=0&size=10
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> getBookings(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Fetched bookings successfully",
                bookingService.getBookings(status, page, size)));
    }

    // FR-08: PUT /api/v1/manager/bookings/5/status  body: {"status":"CONFIRMED"}
    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<BookingResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody BookingStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Booking status updated successfully",
                bookingService.updateStatus(id, request.getStatus())));
    }
}