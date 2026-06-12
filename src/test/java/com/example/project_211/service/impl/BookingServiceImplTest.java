package com.example.project_211.service.impl;

import com.example.project_211.dto.request.BookingRequest;
import com.example.project_211.dto.response.BookingResponse;
import com.example.project_211.entity.*;
import com.example.project_211.enums.RoleName;
import com.example.project_211.exception.BookingConflictException;
import com.example.project_211.repository.*;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Test logic dat san o tang service, khong khoi dong Spring
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
    private TimeSlot slot1;
    private TimeSlot slot2;
    private BookingRequest request;

    @BeforeEach
    void setUp() {
        Role role = Role.builder().id(3L).name(RoleName.ROLE_CUSTOMER).build();
        customer = User.builder().id(1L).username("customer1").role(role).build();
        court = Court.builder().id(1L).name("San so 1")
                .pricePerHour(new BigDecimal("80000")).build();
        slot1 = TimeSlot.builder().id(3L)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(9, 0)).build();
        slot2 = TimeSlot.builder().id(4L)
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0)).build();

        // Dat 2 khung gio lien nhau trong cung mot lan dat
        request = new BookingRequest();
        request.setCourtId(1L);
        request.setTimeSlotIds(List.of(3L, 4L));
        request.setBookingDate(LocalDate.now().plusDays(1));
    }

    @Test
    @DisplayName("TEST 3 - Dat 2 khung gio thanh cong: tra ve 2 don trang thai PENDING")
    void createBooking_shouldSucceed_whenSlotsAreFree() {
        when(userRepository.findByUsername("customer1")).thenReturn(Optional.of(customer));
        when(courtRepository.findById(1L)).thenReturn(Optional.of(court));
        when(timeSlotRepository.findById(3L)).thenReturn(Optional.of(slot1));
        when(timeSlotRepository.findById(4L)).thenReturn(Optional.of(slot2));
        // Ca 2 khung gio deu trong
        when(bookingRepository.existsByCourtIdAndTimeSlotIdAndBookingDateAndStatusIn(
                anyLong(), anyLong(), any(), anyCollection()))
                .thenReturn(false);
        // Gia lap luu danh sach, gan id cho tung don
        when(bookingRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<Booking> list = inv.getArgument(0);
            long id = 10L;
            for (Booking b : list) {
                b.setId(id++);
            }
            return list;
        });

        List<BookingResponse> result = bookingService.createBooking("customer1", request);

        // Tra ve dung 2 don, tat ca deu PENDING
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStatus()).isEqualTo("PENDING");
        assertThat(result.get(1).getStatus()).isEqualTo("PENDING");
        assertThat(result.get(0).getCourtName()).isEqualTo("San so 1");
        verify(bookingRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("TEST 4 - Trung lich: nem BookingConflictException, khong luu don nao")
    void createBooking_shouldThrow_whenSlotIsTaken() {
        when(userRepository.findByUsername("customer1")).thenReturn(Optional.of(customer));
        when(courtRepository.findById(1L)).thenReturn(Optional.of(court));
        when(timeSlotRepository.findById(3L)).thenReturn(Optional.of(slot1));
        when(timeSlotRepository.findById(4L)).thenReturn(Optional.of(slot2));
        // Khung gio dau tien da co nguoi dat
        when(bookingRepository.existsByCourtIdAndTimeSlotIdAndBookingDateAndStatusIn(
                anyLong(), anyLong(), any(), anyCollection()))
                .thenReturn(true);

        assertThatThrownBy(() -> bookingService.createBooking("customer1", request))
                .isInstanceOf(BookingConflictException.class);

        // Quan trong: khong duoc luu bat ky don nao xuong co so du lieu
        verify(bookingRepository, never()).saveAll(anyList());
    }
}
