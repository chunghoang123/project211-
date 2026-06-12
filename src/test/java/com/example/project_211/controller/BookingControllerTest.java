package com.example.project_211.controller;

import com.example.project_211.dto.request.BookingRequest;
import com.example.project_211.dto.response.BookingResponse;
import com.example.project_211.exception.BookingConflictException;
import com.example.project_211.service.BookingService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Test tang controller dat san, gia lap HTTP request bang MockMvc
@WebMvcTest(BookingController.class)
@AutoConfigureMockMvc(addFilters = false)
class BookingControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean
    private BookingService bookingService;

    // Tao request dat 2 khung gio
    private BookingRequest buildRequest() {
        BookingRequest request = new BookingRequest();
        request.setCourtId(1L);
        request.setTimeSlotIds(List.of(3L, 4L));
        request.setBookingDate(LocalDate.now().plusDays(1));
        return request;
    }

    @Test
    @DisplayName("TEST 8 - Dat san thanh cong: tra 201, danh sach don trang thai PENDING")
    void createBooking_shouldReturn201() throws Exception {
        List<BookingResponse> responses = List.of(
                BookingResponse.builder()
                        .id(10L).customerUsername("customer1")
                        .courtName("San so 1").status("PENDING").build(),
                BookingResponse.builder()
                        .id(11L).customerUsername("customer1")
                        .courtName("San so 1").status("PENDING").build());
        when(bookingService.createBooking(eq("customer1"), any(BookingRequest.class)))
                .thenReturn(responses);

        mockMvc.perform(post("/api/v1/customer/bookings")
                        // Gia lap nguoi dung da dang nhap
                        .principal(new UsernamePasswordAuthenticationToken("customer1", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[1].status").value("PENDING"));
    }

    @Test
    @DisplayName("TEST 9 - Trung lich: tra 409 dung format loi")
    void createBooking_shouldReturn409_whenConflict() throws Exception {
        when(bookingService.createBooking(eq("customer1"), any(BookingRequest.class)))
                .thenThrow(new BookingConflictException(
                        "Khung giờ 08:00 - 09:00 đã được đặt trong ngày đã chọn"));

        mockMvc.perform(post("/api/v1/customer/bookings")
                        .principal(new UsernamePasswordAuthenticationToken("customer1", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Xung đột dữ liệu"));
    }
}
