package com.gkcontas.scheduler.web;

import com.gkcontas.scheduler.exception.InvalidCronExpressionException;
import com.gkcontas.scheduler.exception.UnknownJobException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UnknownJobException.class)
    public ProblemDetail handleUnknownJob(UnknownJobException exception) {
        return problem(HttpStatus.NOT_FOUND, "Unknown job", exception.getMessage());
    }

    @ExceptionHandler(InvalidCronExpressionException.class)
    public ProblemDetail handleInvalidCron(InvalidCronExpressionException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid cron expression", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalidBody(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> "%s %s".formatted(error.getField(), error.getDefaultMessage()))
                .orElse("Invalid request body.");
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", detail);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleInvalidParameter(HandlerMethodValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request parameter",
                "One or more request parameters are out of range.");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(title);
        problemDetail.setDetail(detail);
        return problemDetail;
    }
}
