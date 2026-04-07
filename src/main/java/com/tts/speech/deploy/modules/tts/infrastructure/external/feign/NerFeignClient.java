package com.tts.speech.deploy.modules.tts.infrastructure.external.feign;

import com.tts.speech.deploy.modules.tts.infrastructure.external.dto.NerReqDTO;
import com.tts.speech.deploy.modules.tts.infrastructure.external.feign.config.NerFeignClientConfig;
import com.tts.speech.deploy.modules.tts.infrastructure.external.feign.fallback.NerFallbackFactory;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * NER Feign 客户端。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@FeignClient(
    name = "nerFeignClient",
    url = "${kaihu.speech.ner.url}",
    configuration = NerFeignClientConfig.class,
    fallbackFactory = NerFallbackFactory.class)
public interface NerFeignClient {

    /**
     * 调用 NER 服务。
     *
     * @param requestDTO 请求体
     * @return 响应体
     */
    @PostMapping("${kaihu.speech.ner.path}")
    Map<String, Object> extract(@RequestBody NerReqDTO requestDTO);
}
