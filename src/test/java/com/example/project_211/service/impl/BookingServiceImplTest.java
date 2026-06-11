package com.example.project_211.service.impl;

import com.example.project_211.dto.request.BookingRequest;
import com.example.project_211.dto.response.BookingResponse;
import com.example.project_211.entity.*;
import com.example.project_211.enums.BookingStatus;
import com.example.project_211.enums.RoleName;
import com.example.project_211.exception.BookingConflictException;
import com.example.project_211.repository.*;
import com.example.project_211.service.impl.BookingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;
    @Mock private CourtRepository courtRepository;
    @Mock private TimeSlotRepository timeSlotRepository;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private User customer;
    private Court court;
    private TimeSlot slot;
    private BookingRequest request;

    @BeforeEach
    void setUp() {
        Role role = Role.builder().id(3L).name(RoleName.ROLE_CUSTOMER).build();
        customer = User.builder().id(1L).username("customer1").role(role).build();
        court = Court.builder().id(1L).name("San so 1")
                .pricePerHour(new BigDecimal("80000")).build();
        slot = TimeSlot.builder().id(3L)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0)).build();

        request = new BookingRequest();
        request.setCourtId(1L);
        request.setTimeSlotId(3L);
        request.setBookingDate(LocalDate.now().plusDays(1));
    }

    @Test
    @DisplayName("TEST 3 - Dat san thanh cong: trang thai PENDING (UC-04)")
    void createBooking_shouldSucceed_whenSlotIsFree() {
        when(userRepository.findByUsername("customer1")).thenReturn(Optional.of(customer));
        when(courtRepository.findById(1L)).thenReturn(Optional.of(court));
        when(timeSlotRepository.findById(3L)).thenReturn(Optional.of(slot));
        when(bookingRepository.existsByCourtIdAndTimeSlotIdAndBookingDateAndStatusIn(
                anyLong(), anyLong(), any(), anyCollection()))
                .thenReturn(false);                            // slot trong
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            b.setId(10L);
            return b;
        });

        BookingResponse result = bookingService.createBooking("customer1", request);

        assertThat(result.getStatus()).isEqualTo("PENDING");   // UC-04 buoc 3
        assertThat(result.getCourtName()).isEqualTo("San so 1");
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    @DisplayName("TEST 4 - Trung lich: nem BookingConflictException (409), KHONG luu DB")
    void createBooking_shouldThrow_whenSlotIsTaken() {
        when(userRepository.findByUsername("customer1")).thenReturn(Optional.of(customer));
        when(courtRepository.findById(1L)).thenReturn(Optional.of(court));
        when(timeSlotRepository.findById(3L)).thenReturn(Optional.of(slot));
        when(bookingRepository.existsByCourtIdAndTimeSlotIdAndBookingDateAndStatusIn(
                anyLong(), anyLong(), any(), anyCollection()))
                .thenReturn(true);                             // da co nguoi dat!

        assertThatThrownBy(() -> bookingService.createBooking("customer1", request))
                .isInstanceOf(BookingConflictException.class);

        verify(bookingRepository, never()).save(any());        // tuyet doi khong luu
    }
}