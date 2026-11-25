package io.github.wangkui.triagedeck.alert.api;

import java.util.UUID;

import io.github.wangkui.triagedeck.alert.domain.AlertIngestValidationException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = AlertIngestController.class)
public class AlertIngestExceptionHandler {

    @ExceptionHandler({
        AlertIngestValidationException.class,
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<AlertApiError> handleValidationFailure(
            Exception exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new AlertApiError(
                        "VALIDATION_FAILED",
                        "告警请求不符合字段约束",
                        traceId(request)));
    }

    private static String traceId(HttpServletRequest request) {
        String supplied = request.getHeader("X-Trace-Id");
        return supplied != null && !supplied.isBlank() && supplied.length() <= 64
                ? supplied
                : UUID.randomUUID().toString();
    }
}
