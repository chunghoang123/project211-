package com.example.project_211.aspect;

import com.example.project_211.dto.response.BookingResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    // Ghi nhận thời gian thực thi của các phương thức trong tầng Service
    @Around("execution(* com.example.project_211.service.impl..*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        long start = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();

            long duration = System.currentTimeMillis() - start;

            log.info("[THỜI GIAN] {}.{}() thực hiện thành công trong {} ms",
                    className, methodName, duration);

            return result;

        } catch (Throwable ex) {

            long duration = System.currentTimeMillis() - start;

            log.error("[THỜI GIAN] {}.{}() thực hiện thất bại sau {} ms - Lý do: {}",
                    className, methodName, duration, ex.getMessage());

            throw ex;
        }
    }

    // Ghi nhật ký khi đặt sân thành công
    @AfterReturning(
            pointcut = "execution(* com.example.project_211.service.impl.BookingServiceImpl.createBooking(..))",
            returning = "result")
    public void logBookingSuccess(JoinPoint joinPoint, Object result) {

        if (result instanceof List<?> bookings) {

            for (Object item : bookings) {

                if (item instanceof BookingResponse r) {

                    log.info(
                            "[ĐẶT SÂN THÀNH CÔNG] Khách hàng '{}' đã đặt '{}' vào ngày {} từ {} đến {}",
                            r.getCustomerUsername(),
                            r.getCourtName(),
                            r.getBookingDate(),
                            r.getStartTime(),
                            r.getEndTime()
                    );
                }
            }
        }
    }

    // Ghi nhật ký khi đặt sân thất bại
    @AfterThrowing(
            pointcut = "execution(* com.example.project_211.service.impl.BookingServiceImpl.createBooking(..))",
            throwing = "ex")
    public void logBookingFailed(JoinPoint joinPoint, Throwable ex) {

        Object[] args = joinPoint.getArgs();

        String username = args.length > 0
                ? String.valueOf(args[0])
                : "Không xác định";

        log.error(
                "[ĐẶT SÂN THẤT BẠI] Khách hàng '{}' đặt sân không thành công - Lý do: {}",
                username,
                ex.getMessage()
        );
    }
}