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
    @Transactional
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
    // ===== FR-07: Xem lich su dat san =====
    private PageResponse<BookingResponse> getMyBookingsLegacy(String username, int page, int size) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Booking> bookingPage = bookingRepository.findByUserId(user.getId(), pageable);
        // Stream API (UC-02): map Entity -> DTO
        List<BookingResponse> content = bookingPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResponse.<BookingResponse>builder()
                .content(content)
                .page(bookingPage.getNumber())
                .size(bookingPage.getSize())
                .totalElements(bookingPage.getTotalElements())
                .totalPages(bookingPage.getTotalPages())
                .build();
    }

    // ===== FR-08: Manager xem danh sach booking (loc status) =====
    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getBookings(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Booking> bookingPage;
        if (status == null || status.isBlank()) {
            bookingPage = bookingRepository.findAll(pageable);
        } else {
            BookingStatus bs = parseStatus(status);
            bookingPage = bookingRepository.findByStatus(bs, pageable);
        }

        List<BookingResponse> content = bookingPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResponse.<BookingResponse>builder()
                .content(content)
                .page(bookingPage.getNumber())
                .size(bookingPage.getSize())
                .totalElements(bookingPage.getTotalElements())
                .totalPages(bookingPage.getTotalPages())
                .build();
    }

    // ===== FR-08: Phe duyet / Tu choi =====
    @Override
    @Transactional
    public BookingResponse updateStatus(Long bookingId, String status) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Booking not found with id: " + bookingId));

        BookingStatus newStatus = parseStatus(status);

        // Chi cho phep CONFIRMED hoac REJECTED (theo State Diagram SRS)
        if (newStatus != BookingStatus.CONFIRMED && newStatus != BookingStatus.REJECTED) {
            throw new IllegalArgumentException(
                    "Status must be CONFIRMED or REJECTED");           // -> 400
        }

        // Chi booking dang PENDING moi duoc duyet/tu choi
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BookingConflictException(
                    "Only PENDING bookings can be approved or rejected");  // -> 409
        }

        booking.setStatus(newStatus);
        return toResponse(bookingRepository.save(booking));
    }

    private BookingStatus parseStatus(String status) {
        try {
            return BookingStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + status);   // -> 400
        }
    }
}
