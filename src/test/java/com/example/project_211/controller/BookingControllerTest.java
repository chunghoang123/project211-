package com.example.project_211.controller;

import com.example.project_211.dto.request.BookingRequest;
import com.example.project_211.dto.response.BookingResponse;
import com.example.project_211.exception.BookingConflictException;
import com.example.project_211.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
@AutoConfigureMockMvc(addFilters = false)
class BookingControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean
    private BookingService bookingService;

    private BookingRequest buildRequest() {
        BookingRequest request = new BookingRequest();
        request.setCourtId(1L);
        request.setTimeSlotId(3L);
        request.setBookingDate(LocalDate.now().plusDays(1));
        return request;
    }

    @Test
    @DisplayName("TEST 8 - Dat san thanh cong: 201 + status PENDING")
    void createBooking_shouldReturn201() throws Exception {
        BookingResponse response = BookingResponse.builder()
                .id(10L).customerUsername("customer1")
                .courtName("San so 1").status("PENDING").build();
        when(bookingService.createBooking(eq("customer1"), any(BookingRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/customer/bookings")
                        // gia lap user da dang nhap - khop authentication.getName()
                        .principal(new UsernamePasswordAuthenticationToken("customer1", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("TEST 9 - Trung lich: 409 Conflict dung format SRS")
    void createBooking_shouldReturn409_whenConflict() throws Exception {
        when(bookingService.createBooking(eq("customer1"), any(BookingRequest.class)))
                .thenThrow(new BookingConflictException(
                        "This time slot is already booked for the selected date"));

        mockMvc.perform(post("/api/v1/customer/bookings")
                        .principal(new UsernamePasswordAuthenticationToken("customer1", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isConflict())                      // 409 (SRS)
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }
}