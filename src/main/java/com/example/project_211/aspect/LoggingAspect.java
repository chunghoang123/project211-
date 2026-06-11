package com.example.project_211.aspect;

import com.example.project_211.dto.response.BookingResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

import java.util.List;

// Ghi log thoi gian thuc hien va nhat ky dat san bang AOP
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    // Do thoi gian thuc hien cua tat ca cac method trong tang service
    @Around("execution(* com.example.project_211.service.impl..*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;
            log.info("[THOI GIAN] {}.{}() thuc hien trong {} ms",
                    className, methodName, duration);
            return result;
        } catch (Throwable ex) {
            long duration = System.currentTimeMillis() - start;
            log.error("[THOI GIAN] {}.{}() that bai sau {} ms - Ly do: {}",
                    className, methodName, duration, ex.getMessage());
            // Nem lai loi de GlobalExceptionHandler tiep tuc xu ly
            throw ex;
        }
    }

    // Ghi nhat ky khi dat san thanh cong, method tra ve danh sach cac don dat
    @AfterReturning(
            pointcut = "execution(* com.example.project_211.service.impl.BookingServiceImpl.createBooking(..))",
            returning = "result")
    public void logBookingSuccess(JoinPoint joinPoint, Object result) {
        List<?> bookings = (List<?>) result;
        for (Object item : bookings) {
            BookingResponse r = (BookingResponse) item;
            log.info("[NHAT KY - THANH CONG] Khach hang {} dat thanh cong {} vao ngay {}, khung gio {} - {}",
                    r.getCustomerUsername(), r.getCourtName(), r.getBookingDate(),
                    r.getStartTime(), r.getEndTime());
        }
    }

    // Ghi nhat ky khi dat san that bai
    @AfterThrowing(
            pointcut = "execution(* com.example.project_211.service.impl.BookingServiceImpl.createBooking(..))",
            throwing = "ex")
    public void logBookingFailed(JoinPoint joinPoint, Throwable ex) {
        // Tham so thu nhat la ten dang nhap cua khach hang
        Object[] args = joinPoint.getArgs();
        log.error("[NHAT KY - THAT BAI] Khach hang {} co gang dat san nhung that bai - Ly do: {}",
                args[0], ex.getMessage());
    }
}
