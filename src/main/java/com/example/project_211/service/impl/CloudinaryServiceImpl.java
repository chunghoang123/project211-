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

    // Gioi han dung luong anh toi da 5MB
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    @Override
    public String upload(MultipartFile file) {
        // Kiem tra dinh dang anh chi cho phep PNG hoac JPG
        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.equals("image/png") && !contentType.equals("image/jpeg"))) {
            throw new IllegalArgumentException("Chỉ chấp nhận ảnh định dạng PNG hoặc JPG");
        }
        // Kiem tra dung luong anh
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("Ảnh phải có dung lượng dưới 5MB");
        }

        try {
            // Tai anh len Cloudinary qua HTTPS
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap("folder", "badminton_courts"));

            // Cloudinary tra ve duong dan anh an toan
            return (String) result.get("secure_url");

        } catch (IOException e) {
            // Mat ket noi hoac sai khoa bi mat thi tra loi 503
            throw new CloudStorageException(
                    "Dịch vụ lưu trữ ảnh tạm thời không khả dụng, vui lòng thử lại sau");
        }
    }
}
