package com.example.project_211.controller;

import com.example.project_211.dto.request.BookingRequest;
import com.example.project_211.dto.response.ApiResponse;
import com.example.project_211.dto.response.BookingResponse;
import com.example.project_211.dto.response.PageResponse;
import com.example.project_211.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;



    @PostMapping
    public ResponseEntity<ApiResponse<List<BookingResponse>>> createBooking(
            Authentication authentication,                 // Spring tu inject tu JWT
            @Valid @RequestBody BookingRequest request) {
        String username = authentication.getName();        // lay tu SecurityContext
        List<BookingResponse> data = bookingService.createBooking(username, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Booking created successfully", data));
    }

    // FR-07: GET /api/v1/customer/bookings?page=0&size=10
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BookingResponse>>> getMyBookings(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Fetched booking history successfully",
                bookingService.getMyBookings(authentication.getName(), page, size)));
    }
}
