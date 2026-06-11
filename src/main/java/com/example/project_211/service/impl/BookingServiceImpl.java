package com.example.project_211.service.impl;

import com.example.project_211.dto.request.BookingRequest;
import com.example.project_211.dto.response.BookingResponse;
import com.example.project_211.dto.response.PageResponse;
import com.example.project_211.entity.*;
import com.example.project_211.enums.BookingStatus;
import com.example.project_211.exception.BookingConflictException;
import com.example.project_211.exception.ResourceNotFoundException;
import com.example.project_211.repository.*;
import com.example.project_211.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final CourtRepository courtRepository;
    private final TimeSlotRepository timeSlotRepository;

    @Override
    @Transactional   // ⭐ all-or-nothing: 1 slot fail -> rollback het, khong ghi gi
    public List<BookingResponse> createBooking(String username, BookingRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy người dùng: " + username));

        Court court = courtRepository.findById(request.getCourtId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy sân có mã: " + request.getCourtId()));

        List<Long> uniqueSlotIds = new LinkedHashSet<>(request.getTimeSlotIds())
                .stream()
                .toList();

        List<TimeSlot> slots = uniqueSlotIds.stream()
                .map(slotId -> timeSlotRepository.findById(slotId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Không tìm thấy khung giờ có mã: " + slotId)))
                .collect(Collectors.toList());

        for (TimeSlot slot : slots) {
            boolean conflicted = bookingRepository
                    .existsByCourtIdAndTimeSlotIdAndBookingDateAndStatusIn(
                            court.getId(), slot.getId(), request.getBookingDate(),
                            List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED));
            if (conflicted) {
                throw new BookingConflictException(
                        "Khung giờ " + slot.getStartTime() + " - " + slot.getEndTime()
                                + " đã được đặt trong ngày đã chọn");
            }
        }

        // VONG 2: tat ca deu trong -> ghi N dong booking
        List<Booking> bookings = slots.stream()
                .map(slot -> Booking.builder()
                        .user(user)
                        .court(court)
                        .timeSlot(slot)
                        .bookingDate(request.getBookingDate())
                        .status(BookingStatus.PENDING)
                        .build())
                .collect(Collectors.toList());

        return bookingRepository.saveAll(bookings).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getMyBookings(
            String username, int page, int size) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy người dùng: " + username));

        Pageable pageable = PageRequest.of(
                page, size, Sort.by("createdAt").descending());
        Page<Booking> bookingPage =
                bookingRepository.findByUserId(user.getId(), pageable);

        return PageResponse.<BookingResponse>builder()
                .content(bookingPage.getContent().stream()
                        .map(this::toResponse)
                        .toList())
                .page(bookingPage.getNumber())
                .size(bookingPage.getSize())
                .totalElements(bookingPage.getTotalElements())
                .totalPages(bookingPage.getTotalPages())
                .build();
    }

    private BookingResponse toResponse(Booking b) {
        return BookingResponse.builder()
                .id(b.getId())
                .customerUsername(b.getUser().getUsername())
                .courtId(b.getCourt().getId())
                .courtName(b.getCourt().getName())
                .bookingDate(b.getBookingDate())
                .startTime(b.getTimeSlot().getStartTime())
                .endTime(b.getTimeSlot().getEndTime())
                .status(b.getStatus().name())
                .createdAt(b.getCreatedAt())
                .build();
    }
}
