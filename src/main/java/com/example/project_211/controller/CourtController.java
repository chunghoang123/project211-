package com.example.project_211.controller;

import com.example.project_211.dto.response.ApiResponse;
import com.example.project_211.dto.response.CourtResponse;
import com.example.project_211.dto.response.TimeSlotResponse;
import com.example.project_211.service.CourtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CourtController {

    private final CourtService courtService;

    // GET /api/v1/courts - dung vi du chuan trong SRS muc VI.1
    @GetMapping("/courts")
    public ResponseEntity<ApiResponse<List<CourtResponse>>> getCourts() {
        return ResponseEntity.ok(ApiResponse.success(
                "Fetched courts successfully", courtService.getAllCourts()));
    }

    // GET /api/v1/time-slots
    @GetMapping("/time-slots")
    public ResponseEntity<ApiResponse<List<TimeSlotResponse>>> getTimeSlots() {
        return ResponseEntity.ok(ApiResponse.success(
                "Fetched time slots successfully", courtService.getAllTimeSlots()));
    }
}