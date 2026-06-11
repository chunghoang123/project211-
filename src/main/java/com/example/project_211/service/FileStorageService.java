package com.example.project_211.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String upload(MultipartFile file);    // tra ve secure URL
}