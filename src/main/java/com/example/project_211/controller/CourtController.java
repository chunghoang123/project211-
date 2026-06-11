package com.example.project_211.controller;

import com.example.project_211.dto.response.ApiResponse;
import com.example.project_211.dto.response.CourtResponse;
import com.example.project_211.dto.response.TimeSlotResponse;
import com.example.project_211.service.CourtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Xem danh sach san va khung gio, ai cung truy cap duoc
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CourtController {

    private final CourtService courtService;

    // Lay danh sach san
    @GetMapping("/courts")
    public ResponseEntity<ApiResponse<List<CourtResponse>>> getCourts() {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách sân thành công", courtService.getAllCourts()));
    }

    // Lay danh sach khung gio
    @GetMapping("/time-slots")
    public ResponseEntity<ApiResponse<List<TimeSlotResponse>>> getTimeSlots() {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách khung giờ thành công", courtService.getAllTimeSlots()));
    }
}
