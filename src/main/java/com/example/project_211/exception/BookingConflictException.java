package com.example.project_211.exception;

public class BookingConflictException extends RuntimeException {
    public BookingConflictException(String message) { super(message); }
}