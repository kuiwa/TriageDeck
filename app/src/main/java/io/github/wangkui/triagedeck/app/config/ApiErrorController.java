package io.github.wangkui.triagedeck.app.config;

import io.github.wangkui.triagedeck.platform.trace.TraceIds;
import io.github.wangkui.triagedeck.security.api.ApiError;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 统一错误 envelope（契约 §0.3）：code/message/traceId。
 * 替换 Boot 默认 BasicErrorController，避免 /error 被安全链拒绝或返回非契约字段。
 */
@RestController
public class ApiErrorController implements ErrorController {

    private final TraceIds traceIds;

    public ApiErrorController(TraceIds traceIds) {
        this.traceIds = traceIds;
    }

    @RequestMapping("/error")
    public ResponseEntity<ApiError> error(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = attribute instanceof Integer code
                ? HttpStatus.resolve(code)
                : null;
        HttpStatus resolved = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(resolved)
                .body(new ApiError(codeFor(resolved), messageFor(resolved), traceIds.current()));
    }

    private static String codeFor(HttpStatus status) {
        return switch (status.value()) {
            case 400 -> "VALIDATION_FAILED";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "ACCESS_DENIED";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 409 -> "ALREADY_CLEARED";
            case 422 -> "EXPORT_TOO_LARGE";
            case 503 -> "AI_UNAVAILABLE";
            default -> "INTERNAL_ERROR";
        };
    }

    private static String messageFor(HttpStatus status) {
        return switch (status.value()) {
            case 400 -> "请求参数不合法";
            case 401 -> "未认证或凭证已失效";
            case 403 -> "无权访问该资源";
            case 404 -> "资源不存在";
            case 405 -> "请求方法不被支持";
            case 409 -> "告警已被清除";
            case 422 -> "请求无法处理";
            case 503 -> "AI 服务暂不可用，请稍后重试";
            default -> "服务内部错误";
        };
    }
}
