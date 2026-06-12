package com.example.project_211.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter
public class BookingRequest {

    @NotNull(message = "Mã sân không được để trống")
    private Long courtId;

    @NotNull(message = "Ngày đặt sân không được để trống")
    @FutureOrPresent(message = "Ngày đặt sân phải là hôm nay hoặc một ngày trong tương lai")
    private LocalDate bookingDate;

    @NotEmpty(message = "Danh sách khung giờ không được để trống")
    private List<Long> timeSlotIds;
}
