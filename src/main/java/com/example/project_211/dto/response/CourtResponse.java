package com.example.project_211.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class CourtResponse {
    private Long id;
    private String name;
    private String description;
    private BigDecimal pricePerHour;
    private boolean active;
    private List<String> images;
}