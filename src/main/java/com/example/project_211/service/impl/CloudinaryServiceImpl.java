package com.example.project_211.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.project_211.exception.CloudStorageException;
import com.example.project_211.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements FileStorageService {

    private final Cloudinary cloudinary;

    private static final long MAX_SIZE = 5 * 1024 * 1024;   // UC-05: duoi 5MB

    @Override
    public String upload(MultipartFile file) {
        // Validate dinh dang + dung luong (UC-05) -> sai thi 400
        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.equals("image/png") && !contentType.equals("image/jpeg"))) {
            throw new IllegalArgumentException("Only PNG/JPG images are allowed");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("Image must be under 5MB");
        }

        try {
            // UC-05 buoc 3-4: SDK truyen file len cloud qua HTTPS
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap("folder", "badminton_courts"));

            // UC-05 buoc 5: cloud tra ve secure URL
            return (String) result.get("secure_url");

        } catch (IOException e) {
            // UC-05 luong ngoai le: mat ket noi / sai secret key -> 503
            throw new CloudStorageException(
                    "Cloud storage service is temporarily unavailable. Please try again later.");
        }
    }
}