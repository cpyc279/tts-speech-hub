package com.tts.speech.deploy.common.config;

import com.tts.speech.deploy.common.interceptor.TraceIdInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc 配置。
 *
 * @author yangchen5
 * @since 2026-03-06 18:08:56
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String ALL_PATH_PATTERN = "/**";

    @Resource
    private TraceIdInterceptor traceIdInterceptor;

    /**
     * 注册 MVC 拦截器。
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 统一补齐 traceId 上下文，便于后续日志排查。
        registry.addInterceptor(traceIdInterceptor).addPathPatterns(ALL_PATH_PATTERN);
    }
}
