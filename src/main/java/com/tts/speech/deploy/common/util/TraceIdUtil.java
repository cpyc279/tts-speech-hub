package com.tts.speech.deploy.common.util;

import com.tts.speech.deploy.common.constant.CommonConstant;
import com.tts.speech.deploy.common.constant.StringPool;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

/**
 * traceId 工具类。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
public final class TraceIdUtil {

    private static final String UUID_DASH = "-";

    private TraceIdUtil() {
    }

    /**
     * 获取可用的 traceId。
     *
     * @param traceId 请求透传 traceId
     * @return traceId
     */
    public static String getOrCreateTraceId(String traceId) {
        // 优先复用传入的 traceId；未传时生成无中划线 UUID，保证日志字段紧凑。
        if (!StringUtils.hasText(traceId)) {
            return UUID.randomUUID().toString().replace(UUID_DASH, StringPool.EMPTY);
        }
        return traceId;
    }

    /**
     * 获取当前线程中的 traceId。
     *
     * @return traceId
     */
    public static String getCurrentTraceId() {
        // 从当前线程 MDC 中读取 traceId，若不存在则兜底生成，避免下游拿到空值。
        return getOrCreateTraceId(MDC.get(CommonConstant.TRACE_ID));
    }

    /**
     * 绑定当前线程的 traceId。
     *
     * @param traceId traceId
     */
    public static void bindTraceId(String traceId) {
        // 将 traceId 绑定到当前线程 MDC，确保本线程内日志统一输出同一个链路标识。
        MDC.put(CommonConstant.TRACE_ID, getOrCreateTraceId(traceId));
    }
}
