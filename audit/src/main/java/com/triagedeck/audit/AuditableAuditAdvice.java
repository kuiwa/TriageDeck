package com.triagedeck.audit;

import com.triagedeck.platform.audit.ActorType;
import com.triagedeck.platform.audit.AuditAction;
import com.triagedeck.platform.audit.Auditable;
import com.triagedeck.platform.audit.Outcome;
import com.triagedeck.platform.audit.TargetType;
import com.triagedeck.platform.security.SecurityContext;
import com.triagedeck.platform.trace.TraceIds;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 拦截 {@link Auditable} 标注的 public 业务方法，写审计（T08）。
 * - 成功写 outcome=SUCCESS，抛异常写 outcome=FAILED 后原样抛出；
 * - 审计写入失败只记 error 日志，不影响主流程；
 * - 审计行用 AuditWriter 的 REQUIRES_NEW 事务，主流程回滚不影响审计。
 */
@Component
public class AuditableAuditAdvice implements MethodInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditableAuditAdvice.class);

    private final AuditWriter auditWriter;
    private final SecurityContext securityContext;
    private final TraceIds traceIds;

    public AuditableAuditAdvice(AuditWriter auditWriter,
                                SecurityContext securityContext,
                                TraceIds traceIds) {
        this.auditWriter = auditWriter;
        this.securityContext = securityContext;
        this.traceIds = traceIds;
        log.info("action=audit_advice_initialized");
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        Auditable auditable = method.getAnnotation(Auditable.class);
        if (auditable == null) {
            return invocation.proceed();
        }

        String targetId = resolveTargetId(invocation, auditable.targetIdParam());

        Object result;
        try {
            result = invocation.proceed();
        } catch (Throwable failure) {
            record(auditable, method, targetId, Outcome.FAILED);
            throw failure;
        }

        record(auditable, method, targetId, Outcome.SUCCESS);
        return result;
    }

    private void record(Auditable auditable, Method method, String targetId, Outcome outcome) {
        try {
            String actor = securityContext.getCurrentUser();
            ActorType actorType = actor != null ? ActorType.USER : ActorType.SYSTEM;
            String effectiveActor = actor != null ? actor : "SYSTEM";

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("method", method.getName());
            detail.put("targetIdParam", auditable.targetIdParam());
            detail.put("outcome", outcome.name());

            log.info("action=audit_record action={} outcome={} targetType={} targetId={}",
                    auditable.action(), outcome, auditable.targetType(), targetId);

            auditWriter.write(new AuditCommand(
                    UUID.randomUUID(),
                    Instant.now(),
                    effectiveActor,
                    actorType,
                    AuditAction.valueOf(auditable.action()),
                    TargetType.valueOf(auditable.targetType()),
                    targetId,
                    outcome,
                    "APP",
                    null,
                    null,
                    traceIds.current(),
                    detail));
        } catch (Exception e) {
            // 审计失败不影响主流程（AGENTS：catch + error 日志，不打印敏感信息）
            log.error("action=audit_write_failed auditAction={} reason={}",
                    auditable.action(), e.toString());
        }
    }

    private String resolveTargetId(MethodInvocation invocation, String targetIdParam) {
        if (targetIdParam == null || targetIdParam.isBlank()) {
            return null;
        }
        java.lang.reflect.Parameter[] parameters = invocation.getMethod().getParameters();
        Object[] args = invocation.getArguments();
        for (int i = 0; i < parameters.length && i < args.length; i++) {
            if (targetIdParam.equals(parameters[i].getName())) {
                return toTargetId(args[i]);
            }
        }
        // 参数名不可用时的兜底：单参数方法直接用该参数
        if (args.length == 1) {
            return toTargetId(args[0]);
        }
        return null;
    }

    private String toTargetId(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof UUID uuid ? uuid.toString() : String.valueOf(value);
    }
}