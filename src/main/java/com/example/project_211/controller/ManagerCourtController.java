package com.example.project_211.controller;

import com.example.project_211.dto.response.ApiResponse;
import com.example.project_211.service.CourtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manager/courts")
@RequiredArgsConstructor
public class ManagerCourtController {

    private final CourtService courtService;


    @PostMapping("/{id}/images")
    public ResponseEntity<ApiResponse<List<String>>> uploadImages(
            @PathVariable Long id,
            @RequestParam("files") MultipartFile[] files) {
        return ResponseEntity.ok(ApiResponse.success(
                "Images uploaded successfully",
                courtService.uploadCourtImages(id, files)));
    }
}