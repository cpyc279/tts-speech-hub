package com.tts.speech.deploy.modules.tts.infrastructure.external.feign;

import com.tts.speech.deploy.modules.tts.infrastructure.external.dto.NerLargeModelReqDTO;
import com.tts.speech.deploy.modules.tts.infrastructure.external.dto.NerLargeModelRespDTO;
import com.tts.speech.deploy.modules.tts.infrastructure.external.feign.config.NerLargeModelFeignClientConfig;
import com.tts.speech.deploy.modules.tts.infrastructure.external.feign.fallback.NerLargeModelFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 新版 NER 大模型 Feign 客户端。
 *
 * @author yangchen5
 * @since 2026-03-26 00:00:00
 */
@FeignClient(
    name = "nerLargeModelFeignClient",
    url = "${kaihu.speech.ner.large-model.url}",
    configuration = NerLargeModelFeignClientConfig.class,
    fallbackFactory = NerLargeModelFallbackFactory.class)
public interface NerLargeModelFeignClient {

    /**
     * 调用新版 NER 大模型接口。
     *
     * @param remoteIp 调用方 IP
     * @param remoteSvc 调用方服务名
     * @param sessionId 会话 ID
     * @param traceId 链路追踪 ID
     * @param userId 用户 ID
     * @param requestDTO 请求体
     * @return 响应 DTO
     */
    @PostMapping("${kaihu.speech.ner.large-model.path}")
    NerLargeModelRespDTO chat(
        @RequestHeader("X-Remote-Ip") String remoteIp,
        @RequestHeader("X-Remote-Svc") String remoteSvc,
        @RequestHeader("X-Session-Id") String sessionId,
        @RequestHeader("X-Trace-Id") String traceId,
        @RequestHeader("X-User-Id") String userId,
        @RequestBody NerLargeModelReqDTO requestDTO);
}
