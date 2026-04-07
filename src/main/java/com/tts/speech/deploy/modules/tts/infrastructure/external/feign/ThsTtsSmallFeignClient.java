package com.tts.speech.deploy.modules.tts.infrastructure.external.feign;

import com.tts.speech.deploy.modules.tts.infrastructure.external.dto.ThsSmallTtsRespDTO;
import com.tts.speech.deploy.modules.tts.infrastructure.external.feign.config.TtsFeignClientConfig;
import com.tts.speech.deploy.modules.tts.infrastructure.external.feign.fallback.ThsTtsSmallFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 同花顺小模型 TTS Feign 客户端。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@FeignClient(
    name = "thsTtsSmallFeignClient",
    url = "${kaihu.speech.tts.endpoint.small-model-url}",
    configuration = TtsFeignClientConfig.class,
    fallbackFactory = ThsTtsSmallFallbackFactory.class)
public interface ThsTtsSmallFeignClient {

    /**
     * 调用小模型 TTS。
     *
     * @param param 参数 JSON
     * @param timestamp 时间戳
     * @param secretKey 签名
     * @return 下游响应
     */
    @PostMapping(
        value = "${kaihu.speech.tts.endpoint.small-model-path}",
        consumes = "application/x-www-form-urlencoded;charset=UTF-8")
    ThsSmallTtsRespDTO synthesize(
        @RequestParam("param") String param,
        @RequestParam("ts") String timestamp,
        @RequestParam("secretKey") String secretKey);
}
