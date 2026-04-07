package com.tts.speech.deploy.modules.tts.infrastructure.external.feign;

import com.tts.speech.deploy.modules.tts.infrastructure.external.dto.ThsLargeTtsReqDTO;
import com.tts.speech.deploy.modules.tts.infrastructure.external.dto.ThsLargeTtsRespDTO;
import com.tts.speech.deploy.modules.tts.infrastructure.external.feign.config.TtsFeignClientConfig;
import com.tts.speech.deploy.modules.tts.infrastructure.external.feign.fallback.ThsTtsLargeFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 同花顺大模型 TTS Feign 客户端。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@FeignClient(
    name = "thsTtsLargeFeignClient",
    url = "${kaihu.speech.tts.endpoint.large-model-url}",
    configuration = TtsFeignClientConfig.class,
    fallbackFactory = ThsTtsLargeFallbackFactory.class)
public interface ThsTtsLargeFeignClient {

    /**
     * 调用大模型 TTS。
     *
     * @param authorization 授权头
     * @param requestDTO 请求体
     * @return 下游响应
     */
    @PostMapping(value = "${kaihu.speech.tts.endpoint.large-model-path}")
    ThsLargeTtsRespDTO synthesize(
        @RequestHeader("Authorization") String authorization,
        @RequestBody ThsLargeTtsReqDTO requestDTO);
}
