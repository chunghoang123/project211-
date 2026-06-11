package com.example.project_211.dto.response;

import lombok.*;
import java.time.LocalTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class TimeSlotResponse {
    private Long id;
    private LocalTime startTime;
    private LocalTime endTime;
}