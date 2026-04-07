package com.tts.speech.deploy.modules.tts.infrastructure.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 语音服务 TTS 配置。
 *
 * @author yangchen5
 * @since 2026-03-06 21:00:00
 */
@Data
@ConfigurationProperties(prefix = "kaihu.speech.tts")
public class SpeechTtsProperties {

    /**
     * 单次请求允许的最大文本条数。
     */
    private Integer maxBatchSize;

    /**
     * 单条文本允许的最大字符数。
     */
    private Integer maxTextLength;

    /**
     * TTS 预处理线程池配置。
     */
    private ExecutorConfig prepareExecutor = new ExecutorConfig();

    /**
     * TTS 合成线程池配置。
     */
    private ExecutorConfig synthesizeExecutor = new ExecutorConfig();

    /**
     * TTS OSS 上传线程池配置。
     */
    private ExecutorConfig ossUploadExecutor = new ExecutorConfig();

    /**
     * 券商语音路由配置。
     */
    private VoiceRouteConfig voiceRoute = new VoiceRouteConfig();

    /**
     * 下游接口配置。
     */
    private EndpointConfig endpoint = new EndpointConfig();

    /**
     * TTS Feign 调用配置。
     */
    private FeignConfig feign = new FeignConfig();

    /**
     * 缓存配置。
     */
    private CacheConfig cache = new CacheConfig();

    /**
     * SSML 配置。
     */
    private SsmlConfig ssml = new SsmlConfig();

    /**
     * name 标签配置。
     */
    private NameTagConfig nameTag = new NameTagConfig();

    /**
     * 异步线程池配置。
     */
    @Data
    public static class ExecutorConfig {

        /**
         * 核心线程数。
         */
        private Integer corePoolSize;

        /**
         * 最大线程数。
         */
        private Integer maxPoolSize;

        /**
         * 队列容量。
         */
        private Integer queueCapacity;
    }

    /**
     * 语音路由配置。
     */
    @Data
    public static class VoiceRouteConfig {

        /**
         * 默认模型编码。
         */
        private String defaultModelCode;

        /**
         * 券商到模型编码映射。
         */
        private Map<String, String> brokerModelCodeMap = new HashMap<>();

        /**
         * 首选端点映射。
         */
        private Map<String, String> primaryEndpointMap = new HashMap<>();

        /**
         * 备用端点映射。
         */
        private Map<String, String> fallbackEndpointMap = new HashMap<>();
    }

    /**
     * 下游端点配置。
     */
    @Data
    public static class EndpointConfig {

        /**
         * 小模型接口地址。
         */
        private String smallModelUrl;

        /**
         * 大模型接口地址。
         */
        private String largeModelUrl;

        /**
         * 小模型接口路径。
         */
        private String smallModelPath;

        /**
         * 大模型接口路径。
         */
        private String largeModelPath;

        /**
         * 小模型引擎名称。
         */
        private String smallModelEngineName;

        /**
         * 小模型表单参数配置。
         */
        private SmallModelFormConfig smallModelForm = new SmallModelFormConfig();

        /**
         * 大模型音色 ID。
         */
        private String largeModelVoiceId;

        /**
         * 下游应用 ID。
         */
        private String appId;

        /**
         * 下游应用密钥。
         */
        private String appKey;
    }

    /**
     * TTS Feign 配置。
     */
    @Data
    public static class FeignConfig {

        /**
         * Feign 请求超时时间，单位毫秒。
         */
        private Integer timeoutMs;

        /**
         * Feign 最大尝试总次数，包含首次调用。
         */
        private Integer retryMaxAttempts;
    }

    /**
     * 小模型表单参数配置。
     */
    @Data
    public static class SmallModelFormConfig {

        /**
         * 音频类型。
         */
        private Integer audioType;

        /**
         * 音高。
         */
        private Integer pitch;

        /**
         * 音量。
         */
        private Integer volume;

        /**
         * 采样率。
         */
        private Integer samplingRate;

        /**
         * 采样位深。
         */
        private Integer sampleDepth;

        /**
         * 语速。
         */
        private Integer speed;
    }

    /**
     * 缓存配置。
     */
    @Data
    public static class CacheConfig {

        /**
         * Base64 缓存过期时间，单位秒。
         */
        private Long base64TtlSeconds;

        /**
         * Base64 大 key Redis 命令超时时间，单位毫秒。
         */
        private Integer base64CommandTimeoutMs;

        /**
         * Base64 澶?key Redis 杩炴帴瓒呮椂鏃堕棿锛屽崟浣嶆绉掋€?         */
        private Integer base64ConnectTimeoutMs;

        /**
         * 巡检缓存过期时间，单位秒。
         */

        /**
         * Base64 缓存前缀。
         */
        private String base64KeyPrefix;

        /**
         * Base64 缓存索引前缀。
         */
        private String base64IndexKeyPrefix;

        /**
         * 音频 URL 缓存索引前缀
         */
        private String urlKeyPrefix;

        /**
         * 单个券商允许保留的音频缓存上限。
         */
        private Integer base64MaxCountPerBroker;

        /**
         * 巡检缓存 Hash Key。
         */
    }

    /**
     * SSML 配置。
     */
    @Data
    public static class SsmlConfig {

        /**
         * SSML 起始标签。
         */
        private String speakStartTag;

        /**
         * SSML 结束标签。
         */
        private String speakEndTag;
    }

    /**
     * name 标签配置。
     */
    @Data
    public static class NameTagConfig {

        /**
         * name 标签起始标签。
         */
        private String nameTagStartTag;

        /**
         * name 标签结束标签。
         */
        private String nameTagEndTag;

        /**
         * 兼容的 name 标签开始标签列表。
         */
        private List<String> compatibleNameTagStartTagList = new ArrayList<>();
    }
}
