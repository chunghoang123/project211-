package com.example.project_211.service.impl;

import com.example.project_211.dto.response.CourtResponse;
import com.example.project_211.dto.response.TimeSlotResponse;
import com.example.project_211.entity.Court;
import com.example.project_211.entity.CourtImage;
import com.example.project_211.exception.ResourceNotFoundException;
import com.example.project_211.repository.CourtImageRepository;
import com.example.project_211.repository.CourtRepository;
import com.example.project_211.repository.TimeSlotRepository;
import com.example.project_211.service.CourtService;
import com.example.project_211.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourtServiceImpl implements CourtService {

    private final CourtRepository courtRepository;
    private final CourtImageRepository courtImageRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional(readOnly = true)
    public List<CourtResponse> getAllCourts() {
        return courtRepository.findAll().stream()
                .filter(Court::isActive)
                .map(c -> CourtResponse.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .description(c.getDescription())
                        .pricePerHour(c.getPricePerHour())
                        .active(c.isActive())
                        .images(c.getImages().stream()
                                .map(CourtImage::getImageUrl)
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimeSlotResponse> getAllTimeSlots() {
        return timeSlotRepository.findAll().stream()
                .map(t -> TimeSlotResponse.builder()
                        .id(t.getId())
                        .startTime(t.getStartTime())
                        .endTime(t.getEndTime())
                        .build())
                .collect(Collectors.toList());
    }


    @Override
    @Transactional
    public List<String> uploadCourtImages(Long courtId, MultipartFile[] files) {
        Court court = courtRepository.findById(courtId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Court not found with id: " + courtId));

        // Stream API: upload tung file -> URL -> CourtImage entity
        List<CourtImage> images = Arrays.stream(files)
                .map(fileStorageService::upload)               // file -> secure URL
                .map(url -> CourtImage.builder()
                        .imageUrl(url)
                        .court(court)
                        .build())
                .collect(Collectors.toList());

        courtImageRepository.saveAll(images);

        return images.stream()
                .map(CourtImage::getImageUrl)
                .collect(Collectors.toList());
    }
}