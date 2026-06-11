package com.example.project_211.service;

import com.example.project_211.dto.request.BookingRequest;
import com.example.project_211.dto.response.BookingResponse;
import com.example.project_211.dto.response.PageResponse;

import java.util.List;

public interface BookingService {
    List<BookingResponse> createBooking(String username, BookingRequest request);


    PageResponse<BookingResponse> getMyBookings(String username, int page, int size);


    PageResponse<BookingResponse> getBookings(String status, int page, int size);
    BookingResponse updateStatus(Long bookingId, String status);
}
