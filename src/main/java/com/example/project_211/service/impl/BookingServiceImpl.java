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

    // Dat san cho mot hoac nhieu khung gio cung luc
    @Override
    @Transactional
    public List<BookingResponse> createBooking(String username, BookingRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy người dùng: " + username));

        Court court = courtRepository.findById(request.getCourtId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy sân có mã: " + request.getCourtId()));

        // Loai bo cac khung gio bi trung trong danh sach gui len
        List<Long> uniqueSlotIds = new LinkedHashSet<>(request.getTimeSlotIds())
                .stream()
                .toList();

        // Lay thong tin cac khung gio, thieu mot khung gio nao thi bao loi
        List<TimeSlot> slots = uniqueSlotIds.stream()
                .map(slotId -> timeSlotRepository.findById(slotId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Không tìm thấy khung giờ có mã: " + slotId)))
                .collect(Collectors.toList());

        // Buoc 1: kiem tra tat ca khung gio truoc, chua ghi gi vao co so du lieu
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

        // Buoc 2: tat ca khung gio deu trong thi moi ghi cac dong dat san
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

    // Xem lich su dat san cua chinh nguoi dung dang dang nhap
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

    // Quan ly xem danh sach dat san, co the loc theo trang thai
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

    // Phe duyet hoac tu choi mot dat san
    @Override
    @Transactional
    public BookingResponse updateStatus(Long bookingId, String status) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy đơn đặt sân có mã: " + bookingId));

        BookingStatus newStatus = parseStatus(status);

        // Chi cho phep chuyen sang da duyet hoac tu choi
        if (newStatus != BookingStatus.CONFIRMED && newStatus != BookingStatus.REJECTED) {
            throw new IllegalArgumentException(
                    "Trạng thái chỉ được là CONFIRMED hoặc REJECTED");
        }

        // Chi don dang cho duyet moi duoc phe duyet hoac tu choi
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BookingConflictException(
                    "Chỉ có thể duyệt hoặc từ chối đơn đang ở trạng thái chờ duyệt");
        }

        booking.setStatus(newStatus);
        return toResponse(bookingRepository.save(booking));
    }

    // Chuyen chuoi trang thai thanh enum, sai thi bao loi
    private BookingStatus parseStatus(String status) {
        try {
            return BookingStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ: " + status);
        }
    }

    // Chuyen Entity sang DTO tra ve
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
