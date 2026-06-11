package com.example.project_211.exception;

// UC-05 luong ngoai le: loi ket noi cloud -> 503
public class CloudStorageException extends RuntimeException {
    public CloudStorageException(String message) { super(message); }
}