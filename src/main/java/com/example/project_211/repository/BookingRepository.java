package com.example.project_211.repository;

import com.example.project_211.entity.Booking;
import com.example.project_211.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Collection;

public interface BookingRepository extends JpaRepository<Booking, Long> {


    boolean existsByCourtIdAndTimeSlotIdAndBookingDateAndStatusIn(
            Long courtId, Long timeSlotId, LocalDate bookingDate,
            Collection<BookingStatus> statuses);

    Page<Booking> findByUserId(Long userId, Pageable pageable);
}
