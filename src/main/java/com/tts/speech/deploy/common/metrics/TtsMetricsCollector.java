package com.tts.speech.deploy.common.metrics;

import org.springframework.stereotype.Component;

/**
 * TTS 监控占位组件。
 *
 * <p>项目脱敏发布版不再集成监控告警依赖，保留空实现仅用于兼容现有业务调用。</p>
 *
 * @author yangchen5
 * @since 2026-03-19 00:00:00
 */
@Component
public class TtsMetricsCollector {

    public void recordAudioCacheHit(String brokerId) {
    }

    public void recordAudioCacheMiss(String brokerId) {
    }

    public void recordAudioCacheExpiredCleanup(String brokerId) {
    }

    public void recordAudioCacheCapacityEviction(String brokerId) {
    }

    public void recordAudioCacheDanglingCleanup(String brokerId) {
    }

    public void recordNerTemplateHit(String brokerId) {
    }

    public void recordNerDegradeToRawText(String brokerId) {
    }

    public void recordNerLargeModelSuccess(String brokerId) {
    }

    public void recordNerLargeModelFailed(String brokerId) {
    }

    public void recordNerLargeModelFallback(String brokerId) {
    }

    public void recordNerLargeModelAllFailed(String brokerId) {
    }

    public void recordPrimaryToFallback(String primaryEndpoint, String fallbackEndpoint) {
    }

    public void recordAllFailed(String primaryEndpoint, String fallbackEndpoint) {
    }

    public void recordVendorEmptyAudio(String endpoint) {
    }

    public void recordTemplateMergeFailed(String brokerId) {
    }

    public void recordAsyncUploadSuccess(String brokerId) {
    }

    public void recordAsyncUploadFailed(String brokerId) {
    }

    public void recordRedisDegrade(String operation) {
    }

    public void recordTextLengthLimitRejected(String brokerId) {
    }
}
