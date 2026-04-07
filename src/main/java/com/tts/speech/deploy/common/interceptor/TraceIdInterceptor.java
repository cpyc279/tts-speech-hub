package com.tts.speech.deploy.common.interceptor;

import com.tts.speech.deploy.common.constant.CommonConstant;
import com.tts.speech.deploy.common.util.TraceIdUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * TraceId 拦截器。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Component
public class TraceIdInterceptor implements HandlerInterceptor {

    /**
     * 预处理请求并补充 traceId。
     *
     * @param request 请求对象
     * @param response 响应对象
     * @param handler 处理器
     * @return 是否继续执行
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = TraceIdUtil.getOrCreateTraceId(null);
        MDC.put(CommonConstant.TRACE_ID, traceId);
        request.setAttribute(CommonConstant.TRACE_ID, traceId);
        return true;
    }

    /**
     * 清理 MDC。
     *
     * @param request 请求对象
     * @param response 响应对象
     * @param handler 处理器
     * @param exception 异常对象
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        MDC.clear();
    }
}
