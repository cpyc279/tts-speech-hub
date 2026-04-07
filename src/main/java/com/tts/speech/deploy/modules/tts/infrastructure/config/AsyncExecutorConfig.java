package com.tts.speech.deploy.modules.tts.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步线程池配置。
 *
 * @author yangchen5
 * @since 2026-03-18 10:30:00
 */
@Configuration
public class AsyncExecutorConfig {

    /**
     * 文本预处理线程名前缀。
     */
    private static final String TTS_PREPARE_EXECUTOR_THREAD_NAME_PREFIX = "tts-prepare-executor-";

    /**
     * 厂商合成线程名前缀。
     */
    private static final String TTS_SYNTHESIZE_EXECUTOR_THREAD_NAME_PREFIX = "tts-synthesize-executor-";

    /**
     * OSS 上传线程名前缀。
     */
    private static final String TTS_OSS_UPLOAD_EXECUTOR_THREAD_NAME_PREFIX = "tts-oss-upload-executor-";

    /**
     * 创建文本预处理线程池。
     *
     * @param speechTtsProperties TTS配置
     * @return 线程池执行器
     */
    @Bean("ttsPrepareExecutor")
    public ThreadPoolTaskExecutor ttsPrepareExecutor(SpeechTtsProperties speechTtsProperties) {
        // 预处理线程池专门承接缓存判定之后剩余的模板匹配和NER任务。
        return buildExecutor(
            speechTtsProperties.getPrepareExecutor(),
            TTS_PREPARE_EXECUTOR_THREAD_NAME_PREFIX);
    }

    /**
     * 创建厂商合成线程池。
     *
     * @param speechTtsProperties TTS配置
     * @return 线程池执行器
     */
    @Bean("ttsSynthesizeExecutor")
    public ThreadPoolTaskExecutor ttsSynthesizeExecutor(SpeechTtsProperties speechTtsProperties) {
        // 合成线程池只负责厂商TTS调用，避免与其他IO任务混用。
        return buildExecutor(
            speechTtsProperties.getSynthesizeExecutor(),
            TTS_SYNTHESIZE_EXECUTOR_THREAD_NAME_PREFIX);
    }

    /**
     * 创建OSS上传线程池。
     *
     * @param speechTtsProperties TTS配置
     * @return 线程池执行器
     */
    @Bean("ttsOssUploadExecutor")
    public ThreadPoolTaskExecutor ttsOssUploadExecutor(SpeechTtsProperties speechTtsProperties) {
        // OSS上传线程池独立承接音频上传任务，避免抢占文本预处理线程。
        return buildExecutor(
            speechTtsProperties.getOssUploadExecutor(),
            TTS_OSS_UPLOAD_EXECUTOR_THREAD_NAME_PREFIX);
    }

    /**
     * 构建线程池执行器。
     *
     * @param executorConfig 线程池配置
     * @param threadNamePrefix 线程名前缀
     * @return 线程池执行器
     */
    private static ThreadPoolTaskExecutor buildExecutor(
        SpeechTtsProperties.ExecutorConfig executorConfig,
        String threadNamePrefix) {
        // 每个线程池都使用独立配置，避免不同类型任务互相争抢资源。
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数用于承接稳定流量下的常驻任务并发。
        executor.setCorePoolSize(executorConfig.getCorePoolSize());

        // 最大线程数用于应对突发流量时的短时扩容。
        executor.setMaxPoolSize(executorConfig.getMaxPoolSize());

        // 队列容量决定线程池繁忙时允许堆积的任务数量。
        executor.setQueueCapacity(executorConfig.getQueueCapacity());

        // 线程名前缀用于区分预处理、合成和OSS上传三类线程。
        executor.setThreadNamePrefix(threadNamePrefix);

        // 初始化线程池，使其可以被Spring直接注入使用。
        executor.initialize();
        return executor;
    }
}
