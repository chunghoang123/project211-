package com.example.project_211.config;

import com.example.project_211.entity.*;
import com.example.project_211.enums.RoleName;
import com.example.project_211.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalTime;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CourtRepository courtRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner initData() {
        return args -> {
            for (RoleName rn : RoleName.values()) {
                roleRepository.findByName(rn).orElseGet(() ->
                        roleRepository.save(Role.builder().name(rn).build()));
            }

            // 2. Seed tai khoan admin: admin / admin123
            if (!userRepository.existsByUsername("admin")) {
                Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).get();
                userRepository.save(User.builder()
                        .username("admin")
                        .password(passwordEncoder.encode("admin123"))
                        .email("admin@badminton.com")
                        .fullName("System Admin")
                        .role(adminRole)
                        .build());
            }

            // 3.sân mẫu
            if (courtRepository.count() == 0) {
                courtRepository.save(Court.builder()
                        .name("San so 1").description("San tieu chuan thi dau")
                        .pricePerHour(new BigDecimal("80000")).build());
                courtRepository.save(Court.builder()
                        .name("San so 2").description("San tap luyen")
                        .pricePerHour(new BigDecimal("60000")).build());
            }

            // 4. Seed khung gio 6h -> 22h (moi slot 1 tieng)
            if (timeSlotRepository.count() == 0) {
                for (int h = 6; h < 22; h++) {
                    timeSlotRepository.save(TimeSlot.builder()
                            .startTime(LocalTime.of(h, 0))
                            .endTime(LocalTime.of(h + 1, 0))
                            .build());
                }
            }
        };
    }
}