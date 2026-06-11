package com.example.project_211.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "court_images")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class CourtImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;
}