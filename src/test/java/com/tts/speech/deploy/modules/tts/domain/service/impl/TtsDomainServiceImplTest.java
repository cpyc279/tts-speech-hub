package com.tts.speech.deploy.modules.tts.domain.service.impl;

import com.tts.speech.deploy.common.enums.TtsEndpointEnum;
import com.tts.speech.deploy.common.exception.BizException;
import com.tts.speech.deploy.common.metrics.TtsMetricsCollector;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsAudioCacheEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsAudioEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsPreparedTextEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsRequestEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsResponseEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsRouteEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsVendorResponseEntity;
import com.tts.speech.deploy.modules.tts.domain.repository.BrokerVoiceRouteRepository;
import com.tts.speech.deploy.modules.tts.domain.repository.NerFeignRepository;
import com.tts.speech.deploy.modules.tts.domain.repository.OssStorageRepository;
import com.tts.speech.deploy.modules.tts.domain.repository.TtsCacheRepository;
import com.tts.speech.deploy.modules.tts.domain.repository.TtsVendorFeignRepository;
import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechTtsProperties;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * TtsDomainServiceImpl 单元测试。
 *
 * @author yangchen5
 * @since 2026-03-31 00:00:00
 */
class TtsDomainServiceImplTest {

    /**
     * 普通文本。
     */
    private static final String PLAIN_TEXT = "欢迎办理业务";

    /**
     * 第二条文本。
     */
    private static final String SECOND_TEXT = "欢迎再次办理";

    /**
     * 使用错误格式开始标签的文本。
     */
    private static final String COMPATIBLE_TAGGED_TEXT =
        "<speak>娆㈣繋<say-as interpret-as=name>寮犱笁</say-as>鍔炵悊涓氬姟</speak>";

    /**
     * 标准化后的 name 标签文本。
     */
    private static final String NORMALIZED_TAGGED_TEXT =
        "<speak>娆㈣繋<say-as interpret-as=\"name\">寮犱笁</say-as>鍔炵悊涓氬姟</speak>";

    @Test
    void testPrepareTextsShouldUsePlainTextAudioCacheAndUrlCacheWhenCacheExists() {
        TtsDomainServiceImpl domainService = createDomainService();
        TtsCacheRepository ttsCacheRepository = getTtsCacheRepository(domainService);
        Mockito.when(ttsCacheRepository.getAudioCache(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
            .thenReturn(TtsAudioCacheEntity.builder().base64Audio("cached-base64").build());
        Mockito.when(ttsCacheRepository.getAudioUrlCache(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
            .thenReturn("https://oss.example.com/audio.wav");

        List<TtsPreparedTextEntity> preparedTextEntityList = domainService.prepareTexts(buildRequestEntity(PLAIN_TEXT), "hithink");

        Assertions.assertEquals(1, preparedTextEntityList.size());
        TtsPreparedTextEntity preparedTextEntity = preparedTextEntityList.get(0);
        Assertions.assertEquals("cached-base64", preparedTextEntity.getCachedBase64Audio());
        Assertions.assertEquals("https://oss.example.com/audio.wav", preparedTextEntity.getCachedAudioUrl());
        Assertions.assertTrue(Boolean.TRUE.equals(preparedTextEntity.getAudioCacheHit()));
        Mockito.verifyNoInteractions(getNerFeignRepository(domainService));
    }

    @Test
    void testPrepareTextsShouldTreatCompatibleNameTagAsExistingAndNormalizeText() {
        TtsDomainServiceImpl domainService = createDomainService();
        TtsCacheRepository ttsCacheRepository = getTtsCacheRepository(domainService);

        List<TtsPreparedTextEntity> preparedTextEntityList =
            domainService.prepareTexts(buildRequestEntity(COMPATIBLE_TAGGED_TEXT), "hithink");

        Assertions.assertEquals(1, preparedTextEntityList.size());
        TtsPreparedTextEntity preparedTextEntity = preparedTextEntityList.get(0);
        Assertions.assertEquals(NORMALIZED_TAGGED_TEXT, preparedTextEntity.getRawText());
        Assertions.assertEquals(NORMALIZED_TAGGED_TEXT, preparedTextEntity.getFinalText());
        Assertions.assertTrue(Boolean.TRUE.equals(preparedTextEntity.getHasNameTag()));
        Mockito.verifyNoInteractions(ttsCacheRepository);
        Mockito.verifyNoInteractions(getNerFeignRepository(domainService));
    }

    @Test
    void testSynthesizeShouldReturnCachedAudioDirectlyWhenAllTextsHitCache() {
        TtsDomainServiceImpl domainService = createDomainService();
        BrokerVoiceRouteRepository brokerVoiceRouteRepository = getBrokerVoiceRouteRepository(domainService);
        TtsCacheRepository ttsCacheRepository = getTtsCacheRepository(domainService);
        Mockito.when(brokerVoiceRouteRepository.resolveRoute("broker-a"))
            .thenReturn(buildRouteEntity());
        Mockito.when(ttsCacheRepository.getAudioCache(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
            .thenReturn(TtsAudioCacheEntity.builder().base64Audio("cached-base64").build());
        Mockito.when(ttsCacheRepository.getAudioUrlCache(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
            .thenReturn("https://oss.example.com/audio.wav");

        TtsResponseEntity responseEntity = domainService.synthesize(buildRequestEntity(List.of(PLAIN_TEXT, SECOND_TEXT)));

        Assertions.assertEquals(2, responseEntity.getAudioList().size());
        Assertions.assertEquals("cached-base64", responseEntity.getAudioList().get(0).getBase64Audio());
        Assertions.assertEquals("https://oss.example.com/audio.wav", responseEntity.getAudioList().get(0).getAudioUrl());
        Mockito.verifyNoInteractions(getTtsVendorFeignRepository(domainService));
        Mockito.verifyNoInteractions(getOssStorageRepository(domainService));
        Mockito.verify(ttsCacheRepository, Mockito.never())
            .putAudioCache(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(TtsAudioCacheEntity.class));
        Mockito.verify(ttsCacheRepository, Mockito.never())
            .putAudioUrlCache(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString());
    }

    @Test
    void testSynthesizeShouldUploadCacheHitAudioWhenUrlCacheMissing() {
        TtsDomainServiceImpl domainService = createDomainService();
        BrokerVoiceRouteRepository brokerVoiceRouteRepository = getBrokerVoiceRouteRepository(domainService);
        TtsCacheRepository ttsCacheRepository = getTtsCacheRepository(domainService);
        OssStorageRepository ossStorageRepository = getOssStorageRepository(domainService);
        Mockito.when(brokerVoiceRouteRepository.resolveRoute("broker-a"))
            .thenReturn(buildRouteEntity());
        Mockito.when(ttsCacheRepository.getAudioCache(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
            .thenReturn(TtsAudioCacheEntity.builder().base64Audio("cached-base64").build());
        Mockito.when(ttsCacheRepository.getAudioUrlCache(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
            .thenReturn(null);
        Mockito.when(ossStorageRepository.uploadBase64(
                ArgumentMatchers.eq(PLAIN_TEXT),
                ArgumentMatchers.eq("cached-base64"),
                ArgumentMatchers.eq("trace-1")))
            .thenReturn("https://oss.example.com/retry.wav");

        TtsResponseEntity responseEntity = domainService.synthesize(buildRequestEntity(PLAIN_TEXT));

        Assertions.assertEquals(1, responseEntity.getAudioList().size());
        Mockito.verify(ossStorageRepository).uploadBase64(
            ArgumentMatchers.eq(PLAIN_TEXT),
            ArgumentMatchers.eq("cached-base64"),
            ArgumentMatchers.eq("trace-1"));
        Mockito.verify(ttsCacheRepository).putAudioUrlCache(
            ArgumentMatchers.eq("broker-a"),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.eq("https://oss.example.com/retry.wav"));
        Mockito.verify(ttsCacheRepository).getAudioCache(ArgumentMatchers.eq("broker-a"), ArgumentMatchers.anyString());
        Mockito.verify(ttsCacheRepository).getAudioUrlCache(ArgumentMatchers.eq("broker-a"), ArgumentMatchers.anyString());
        Mockito.verifyNoMoreInteractions(ttsCacheRepository);
    }

    @Test
    void testSynthesizeShouldPersistBase64CacheAndUrlCacheAfterAsyncUploadSuccess() {
        TtsDomainServiceImpl domainService = createDomainService();
        BrokerVoiceRouteRepository brokerVoiceRouteRepository = getBrokerVoiceRouteRepository(domainService);
        TtsCacheRepository ttsCacheRepository = getTtsCacheRepository(domainService);
        TtsVendorFeignRepository ttsVendorFeignRepository = getTtsVendorFeignRepository(domainService);
        OssStorageRepository ossStorageRepository = getOssStorageRepository(domainService);
        NerFeignRepository nerFeignRepository = getNerFeignRepository(domainService);
        ReflectionTestUtils.setField(domainService, "ttsPrepareExecutor", buildFailExecutor());
        Mockito.when(brokerVoiceRouteRepository.resolveRoute("broker-a"))
            .thenReturn(buildRouteEntity());
        Mockito.when(ttsCacheRepository.getAudioCache(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
            .thenReturn(null);
        Mockito.when(ttsCacheRepository.getAudioUrlCache(ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
            .thenReturn(null);
        Mockito.when(nerFeignRepository.matchAvailableTemplate(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString()))
            .thenReturn(Optional.of(PLAIN_TEXT));
        Mockito.when(ttsVendorFeignRepository.synthesize(
                ArgumentMatchers.eq(TtsEndpointEnum.SMALL),
                ArgumentMatchers.eq("hithink"),
                ArgumentMatchers.any(TtsPreparedTextEntity.class),
                ArgumentMatchers.eq("trace-1")))
            .thenReturn(TtsVendorResponseEntity.builder()
                .base64Audio("vendor-base64")
                .vendorTraceId("vendor-trace")
                .synthesizeText(PLAIN_TEXT)
                .build());
        Mockito.when(ossStorageRepository.uploadBase64(
                ArgumentMatchers.eq(PLAIN_TEXT),
                ArgumentMatchers.eq("vendor-base64"),
                ArgumentMatchers.eq("trace-1")))
            .thenReturn("https://oss.example.com/audio.wav");

        TtsResponseEntity responseEntity = domainService.synthesize(buildRequestEntity(PLAIN_TEXT));

        Assertions.assertEquals(1, responseEntity.getAudioList().size());
        Mockito.verify(ttsCacheRepository).putAudioCache(
            ArgumentMatchers.eq("broker-a"),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.argThat(audioCacheEntity -> "vendor-base64".equals(audioCacheEntity.getBase64Audio())));
        Mockito.verify(ttsCacheRepository).putAudioUrlCache(
            ArgumentMatchers.eq("broker-a"),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.eq("https://oss.example.com/audio.wav"));
        Mockito.verify(ttsCacheRepository).getAudioCache(ArgumentMatchers.eq("broker-a"), ArgumentMatchers.anyString());
        Mockito.verify(ttsCacheRepository).getAudioUrlCache(ArgumentMatchers.eq("broker-a"), ArgumentMatchers.anyString());
        Mockito.verifyNoMoreInteractions(ttsCacheRepository);
    }

    @Test
    void testValidateRequestShouldThrowWhenRawTextExceedsMaxTextLength() {
        TtsDomainServiceImpl domainService = createDomainService();
        TtsMetricsCollector ttsMetricsCollector = getTtsMetricsCollector(domainService);

        BizException bizException = Assertions.assertThrows(
            BizException.class,
            () -> domainService.validateRequest(buildRequestEntity("12345678901")));

        Assertions.assertEquals("raw_text exceeds max text length", bizException.getErrorMessage());
        Mockito.verify(ttsMetricsCollector).recordTextLengthLimitRejected("broker-a");
    }

    @Test
    void testExecuteWithFallbackShouldRetryVendorRoundWhenPrimaryFails() {
        TtsDomainServiceImpl domainService = createDomainService();
        TtsVendorFeignRepository ttsVendorFeignRepository = getTtsVendorFeignRepository(domainService);
        TtsPreparedTextEntity preparedTextEntity = TtsPreparedTextEntity.builder()
            .sequence(0)
            .rawText(PLAIN_TEXT)
            .finalText(PLAIN_TEXT)
            .cacheKey("tts:cache:base64:broker-a:hithink:test")
            .hasNameTag(false)
            .audioCacheHit(false)
            .needSynthesize(true)
            .build();
        Mockito.when(ttsVendorFeignRepository.synthesize(
                ArgumentMatchers.eq(TtsEndpointEnum.SMALL),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(TtsPreparedTextEntity.class),
                ArgumentMatchers.anyString()))
            .thenThrow(new RuntimeException("primary failed"));
        Mockito.when(ttsVendorFeignRepository.synthesize(
                ArgumentMatchers.eq(TtsEndpointEnum.LARGE),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(TtsPreparedTextEntity.class),
                ArgumentMatchers.anyString()))
            .thenReturn(TtsVendorResponseEntity.builder()
                .base64Audio("fallback-base64")
                .vendorTraceId("vendor-trace")
                .synthesizeText(PLAIN_TEXT)
                .build());

        List<TtsAudioEntity> audioEntityList = domainService.executeWithFallback(
            List.of(preparedTextEntity),
            buildRouteEntity(),
            "trace-1");

        Assertions.assertEquals(1, audioEntityList.size());
        Assertions.assertEquals("fallback-base64", audioEntityList.get(0).getBase64Audio());
        Assertions.assertEquals(PLAIN_TEXT, audioEntityList.get(0).getSynthesizeText());
        Mockito.verify(ttsVendorFeignRepository).synthesize(
            ArgumentMatchers.eq(TtsEndpointEnum.SMALL),
            ArgumentMatchers.eq("hithink"),
            ArgumentMatchers.argThat(preparedText -> PLAIN_TEXT.equals(preparedText.getFinalText())),
            ArgumentMatchers.eq("trace-1"));
        Mockito.verify(ttsVendorFeignRepository).synthesize(
            ArgumentMatchers.eq(TtsEndpointEnum.LARGE),
            ArgumentMatchers.eq("hithink"),
            ArgumentMatchers.argThat(preparedText -> PLAIN_TEXT.equals(preparedText.getFinalText())),
            ArgumentMatchers.eq("trace-1"));
    }

    @Test
    void testPersistCacheShouldOnlyWriteEligibleAudio() {
        TtsDomainServiceImpl domainService = createDomainService();
        TtsCacheRepository ttsCacheRepository = getTtsCacheRepository(domainService);
        TtsAudioEntity audioEntityNeedPersist = buildAudioEntity(PLAIN_TEXT, false, "base64-1");
        TtsAudioEntity audioEntityWithNameTag = buildAudioEntity(NORMALIZED_TAGGED_TEXT, false, "base64-2");
        TtsAudioEntity cachedAudioEntity = buildAudioEntity(SECOND_TEXT, true, "base64-3");

        domainService.persistCache(
            List.of(audioEntityNeedPersist, audioEntityWithNameTag, cachedAudioEntity),
            "hithink",
            "broker-a");

        Mockito.verify(ttsCacheRepository).putAudioCache(
            ArgumentMatchers.eq("broker-a"),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.argThat(audioCacheEntity -> "base64-1".equals(audioCacheEntity.getBase64Audio())));
        Mockito.verifyNoMoreInteractions(ttsCacheRepository);
    }

    /**
     * 创建领域服务。
     *
     * @return 领域服务
     */
    private TtsDomainServiceImpl createDomainService() {
        TtsDomainServiceImpl domainService = new TtsDomainServiceImpl();
        ReflectionTestUtils.setField(domainService, "brokerVoiceRouteRepository", Mockito.mock(BrokerVoiceRouteRepository.class));
        ReflectionTestUtils.setField(domainService, "ttsCacheRepository", Mockito.mock(TtsCacheRepository.class));
        ReflectionTestUtils.setField(domainService, "nerFeignRepository", Mockito.mock(NerFeignRepository.class));
        ReflectionTestUtils.setField(domainService, "ossStorageRepository", Mockito.mock(OssStorageRepository.class));
        ReflectionTestUtils.setField(domainService, "ttsVendorFeignRepository", Mockito.mock(TtsVendorFeignRepository.class));
        ReflectionTestUtils.setField(domainService, "speechTtsProperties", buildSpeechTtsProperties());
        ReflectionTestUtils.setField(domainService, "ttsPrepareExecutor", buildDirectExecutor());
        ReflectionTestUtils.setField(domainService, "ttsSynthesizeExecutor", buildDirectExecutor());
        ReflectionTestUtils.setField(domainService, "ttsOssUploadExecutor", buildDirectExecutor());
        ReflectionTestUtils.setField(domainService, "ttsMetricsCollector", Mockito.mock(TtsMetricsCollector.class));
        return domainService;
    }

    /**
     * 构建同步执行器。
     *
     * @return 执行器
     */
    private static Executor buildDirectExecutor() {
        return Runnable::run;
    }

    /**
     * 构建不应被调用的执行器。
     *
     * @return 执行器
     */
    private static Executor buildFailExecutor() {
        return command -> {
            throw new AssertionError("executor should not be used");
        };
    }

    /**
     * 构建请求。
     *
     * @param rawText 原始文本
     * @return 请求
     */
    private TtsRequestEntity buildRequestEntity(String rawText) {
        return buildRequestEntity(List.of(rawText));
    }

    /**
     * 构建请求。
     *
     * @param rawTextList 原始文本列表
     * @return 请求
     */
    private TtsRequestEntity buildRequestEntity(List<String> rawTextList) {
        return TtsRequestEntity.builder()
            .brokerId("broker-a")
            .traceId("trace-1")
            .businessCode("KAIHU")
            .userId("user-a")
            .rawTextList(rawTextList)
            .build();
    }

    /**
     * 构建路由实体。
     *
     * @return 路由实体
     */
    private static TtsRouteEntity buildRouteEntity() {
        return TtsRouteEntity.builder()
            .modelCode("hithink")
            .primaryEndpoint(TtsEndpointEnum.SMALL)
            .fallbackEndpoint(TtsEndpointEnum.LARGE)
            .build();
    }

    /**
     * 构建音频实体。
     *
     * @param finalText 最终文本
     * @param cacheHit 是否命中缓存
     * @param base64Audio Base64 音频
     * @return 音频实体
     */
    private static TtsAudioEntity buildAudioEntity(String finalText, boolean cacheHit, String base64Audio) {
        return TtsAudioEntity.builder()
            .sequence(0)
            .rawText(finalText)
            .finalText(finalText)
            .synthesizeText(finalText)
            .base64Audio(base64Audio)
            .audioUrl(null)
            .cacheKey("tts:cache:base64:broker-a:hithink:test")
            .hasNameTag(false)
            .vendorTraceId(null)
            .cacheHit(cacheHit)
            .build();
    }

    /**
     * 构建配置。
     *
     * @return 配置
     */
    private SpeechTtsProperties buildSpeechTtsProperties() {
        SpeechTtsProperties speechTtsProperties = new SpeechTtsProperties();
        speechTtsProperties.setMaxBatchSize(10);
        speechTtsProperties.setMaxTextLength(10);

        SpeechTtsProperties.CacheConfig cacheConfig = new SpeechTtsProperties.CacheConfig();
        cacheConfig.setBase64KeyPrefix("tts:cache:base64");
        cacheConfig.setBase64TtlSeconds(604800L);
        cacheConfig.setUrlKeyPrefix("tts:cache:url");
        speechTtsProperties.setCache(cacheConfig);

        SpeechTtsProperties.NameTagConfig nameTagConfig = new SpeechTtsProperties.NameTagConfig();
        nameTagConfig.setNameTagStartTag("<say-as interpret-as=\"name\">");
        nameTagConfig.setNameTagEndTag("</say-as>");
        nameTagConfig.setCompatibleNameTagStartTagList(List.of(
            "<say-as interpret-as='name'>",
            "<say-as interpret-as=’name‘>",
            "<say-as interpret-as=‘name’>",
            "<say-as interpret-as=name>"));
        speechTtsProperties.setNameTag(nameTagConfig);

        SpeechTtsProperties.SsmlConfig ssmlConfig = new SpeechTtsProperties.SsmlConfig();
        ssmlConfig.setSpeakStartTag("<speak>");
        ssmlConfig.setSpeakEndTag("</speak>");
        speechTtsProperties.setSsml(ssmlConfig);
        return speechTtsProperties;
    }

    /**
     * 获取缓存仓储。
     *
     * @param domainService 领域服务
     * @return 缓存仓储
     */
    private TtsCacheRepository getTtsCacheRepository(TtsDomainServiceImpl domainService) {
        return (TtsCacheRepository) ReflectionTestUtils.getField(domainService, "ttsCacheRepository");
    }

    /**
     * 获取路由仓储。
     *
     * @param domainService 领域服务
     * @return 路由仓储
     */
    private BrokerVoiceRouteRepository getBrokerVoiceRouteRepository(TtsDomainServiceImpl domainService) {
        return (BrokerVoiceRouteRepository) ReflectionTestUtils.getField(domainService, "brokerVoiceRouteRepository");
    }

    /**
     * 获取 NER 仓储。
     *
     * @param domainService 领域服务
     * @return NER 仓储
     */
    private NerFeignRepository getNerFeignRepository(TtsDomainServiceImpl domainService) {
        return (NerFeignRepository) ReflectionTestUtils.getField(domainService, "nerFeignRepository");
    }

    /**
     * 获取 OSS 仓储。
     *
     * @param domainService 领域服务
     * @return OSS 仓储
     */
    private OssStorageRepository getOssStorageRepository(TtsDomainServiceImpl domainService) {
        return (OssStorageRepository) ReflectionTestUtils.getField(domainService, "ossStorageRepository");
    }

    /**
     * 获取厂商仓储。
     *
     * @param domainService 领域服务
     * @return 厂商仓储
     */
    private TtsVendorFeignRepository getTtsVendorFeignRepository(TtsDomainServiceImpl domainService) {
        return (TtsVendorFeignRepository) ReflectionTestUtils.getField(domainService, "ttsVendorFeignRepository");
    }

    /**
     * 获取指标采集器。
     *
     * @param domainService 领域服务
     * @return 指标采集器
     */
    private TtsMetricsCollector getTtsMetricsCollector(TtsDomainServiceImpl domainService) {
        return (TtsMetricsCollector) ReflectionTestUtils.getField(domainService, "ttsMetricsCollector");
    }
}
