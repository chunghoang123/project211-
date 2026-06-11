package com.example.project_211.service;

import com.example.project_211.dto.response.CourtResponse;
import com.example.project_211.dto.response.TimeSlotResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CourtService {
    List<CourtResponse> getAllCourts();
    List<TimeSlotResponse> getAllTimeSlots();
    List<String> uploadCourtImages(Long courtId, MultipartFile[] files);
}