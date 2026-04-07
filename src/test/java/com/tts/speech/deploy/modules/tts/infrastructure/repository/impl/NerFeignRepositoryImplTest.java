package com.tts.speech.deploy.modules.tts.infrastructure.repository.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tts.speech.deploy.common.constant.CommonConstant;
import com.tts.speech.deploy.common.exception.NerFeignException;
import com.tts.speech.deploy.common.metrics.TtsMetricsCollector;
import com.tts.speech.deploy.common.redis.RedisOperator;
import com.tts.speech.deploy.common.util.JsonUtil;
import com.tts.speech.deploy.modules.tts.domain.entity.NerTemplateCacheEntity;
import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechNerProperties;
import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechTtsProperties;
import com.tts.speech.deploy.modules.tts.infrastructure.external.dto.NerLargeModelRespDTO;
import com.tts.speech.deploy.modules.tts.infrastructure.external.feign.NerFeignClient;
import com.tts.speech.deploy.modules.tts.infrastructure.external.feign.NerLargeModelFeignClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * NerFeignRepositoryImpl 单元测试。
 *
 * @author yangchen5
 * @since 2026-03-18 00:00:00
 */
class NerFeignRepositoryImplTest {

    /**
     * 验证未达到阈值的模板不会直接参与匹配。
     */
    @Test
    void testMatchAvailableTemplateShouldSkipTemplateBelowThreshold() {
        NerFeignRepositoryImpl repository = createRepository(2, 200);
        mockTemplateEntries(repository, List.of(buildTemplateEntity("\\Q欢迎\\E(.+?)\\Q办理业务\\E", 1, System.currentTimeMillis())));

        Optional<String> matchedTemplateOptional = repository.matchAvailableTemplate("broker-a", "欢迎张三办理业务", "trace-1");

        Assertions.assertTrue(matchedTemplateOptional.isEmpty());
    }

    /**
     * 验证达到阈值的模板命中后会直接返回最终文本并刷新计数。
     */
    @Test
    void testMatchAvailableTemplateShouldReturnWrappedTextWhenTemplateReachThreshold() {
        NerFeignRepositoryImpl repository = createRepository(2, 200);
        RedisOperator redisOperator = getRedisOperator(repository);
        ListAppender<ILoggingEvent> listAppender = attachTemplateLogAppender();
        mockTemplateEntries(repository, List.of(buildTemplateEntity("\\Q欢迎\\E(.+?)\\Q办理业务\\E", 2, System.currentTimeMillis())));

        Optional<String> matchedTemplateOptional = repository.matchAvailableTemplate("broker-a", "欢迎张三办理业务", "trace-1");

        Assertions.assertTrue(matchedTemplateOptional.isPresent());
        Assertions.assertTrue(matchedTemplateOptional.get().contains("<say-as interpret-as=\"name\">张三</say-as>"));
        Mockito.verify(redisOperator).putHashValue(
            ArgumentMatchers.eq("ner_template_write"),
            ArgumentMatchers.eq("tts:ner:template:broker-a"),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any());
        Assertions.assertTrue(
            containsLog(listAppender, "tts:ner:template:broker-a"),
            "NER 模板缓存命中日志应打印 redis key");
        Assertions.assertFalse(containsLog(listAppender, "sessionId"));
        detachTemplateLogAppender(listAppender);
    }

    /**
     * 验证合并模板时会递增已存在模板的命中次数。
     */
    @Test
    void testMergeTemplateShouldIncreaseUseCountWhenTemplateExists() {
        NerFeignRepositoryImpl repository = createRepository(2, 200);
        RedisOperator redisOperator = getRedisOperator(repository);
        mockTemplateEntries(repository, List.of(buildTemplateEntity("\\Q欢迎\\E(.+?)\\Q办理业务\\E", 2, System.currentTimeMillis())));

        repository.mergeTemplate("broker-a", "欢迎张三办理业务", List.of("张三"), "trace-1");

        Mockito.verify(redisOperator).putHashValue(
            ArgumentMatchers.eq("ner_template_write"),
            ArgumentMatchers.eq("tts:ner:template:broker-a"),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.argThat(templateJson -> templateJson.contains("\"templatePattern\"")),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any());
    }

    /**
     * 验证新版 NER 成功时直接返回新版结果。
     */
    @Test
    void testParsePeopleNamesShouldReturnNamesFromLargeModelWhenLargeModelSuccess() {
        NerFeignRepositoryImpl repository = createRepository(2, 200);
        NerLargeModelFeignClient largeModelFeignClient = getNerLargeModelFeignClient(repository);
        TtsMetricsCollector metricsCollector = getMetricsCollector(repository);
        ListAppender<ILoggingEvent> listAppender = attachNerLogAppender();
        Mockito.when(largeModelFeignClient.chat(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any()))
            .thenReturn(buildLargeModelResponse(200, "{\"name\":[\"张三\",\"李四\"]}"));

        List<String> peopleNameList = repository.parsePeopleNames("broker-a", "user-a", "欢迎张三办理业务", "trace-1");

        Assertions.assertEquals(List.of("张三", "李四"), peopleNameList);
        Mockito.verify(metricsCollector).recordNerLargeModelSuccess("broker-a");
        Mockito.verifyNoInteractions(getNerFeignClient(repository));
        Assertions.assertFalse(containsLog(listAppender, "sessionId"));
        detachNerLogAppender(listAppender);
    }

    /**
     * 验证新版 NER 业务失败时会降级旧版。
     */
    @Test
    void testParsePeopleNamesShouldFallbackToLegacyWhenLargeModelBusinessFailed() {
        NerFeignRepositoryImpl repository = createRepository(2, 200);
        NerLargeModelFeignClient largeModelFeignClient = getNerLargeModelFeignClient(repository);
        NerFeignClient nerFeignClient = getNerFeignClient(repository);
        TtsMetricsCollector metricsCollector = getMetricsCollector(repository);
        ListAppender<ILoggingEvent> listAppender = attachNerLogAppender();
        Mockito.when(largeModelFeignClient.chat(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any()))
            .thenReturn(buildLargeModelResponse(500, "{\"name\":[\"张三\"]}"));
        Mockito.when(nerFeignClient.extract(ArgumentMatchers.any())).thenReturn(buildLegacyPeopleResponse("张三"));

        List<String> peopleNameList = repository.parsePeopleNames("broker-a", "user-a", "欢迎张三办理业务", "trace-1");

        Assertions.assertEquals(List.of("张三"), peopleNameList);
        Mockito.verify(metricsCollector).recordNerLargeModelFailed("broker-a");
        Mockito.verify(metricsCollector).recordNerLargeModelFallback("broker-a");
        Assertions.assertFalse(containsLog(listAppender, "sessionId"));
        detachNerLogAppender(listAppender);
    }

    /**
     * 验证新版 NER 的 name 字段为空时视为成功且返回空列表。
     */
    @Test
    void testParsePeopleNamesShouldReturnEmptyListWhenLargeModelNameFieldIsEmpty() {
        NerFeignRepositoryImpl repository = createRepository(2, 200);
        NerLargeModelFeignClient largeModelFeignClient = getNerLargeModelFeignClient(repository);
        TtsMetricsCollector metricsCollector = getMetricsCollector(repository);
        Mockito.when(largeModelFeignClient.chat(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any()))
            .thenReturn(buildLargeModelResponse(200, "{\"name\":[]}"));

        List<String> peopleNameList = repository.parsePeopleNames("broker-a", "user-a", "欢迎办理业务", "trace-1");

        Assertions.assertTrue(peopleNameList.isEmpty());
        Mockito.verify(metricsCollector).recordNerLargeModelSuccess("broker-a");
        Mockito.verifyNoInteractions(getNerFeignClient(repository));
    }

    /**
     * 验证新版 NER 缺少 name 字段时会降级旧版。
     */
    @Test
    void testParsePeopleNamesShouldFallbackToLegacyWhenLargeModelMissingNameField() {
        NerFeignRepositoryImpl repository = createRepository(2, 200);
        NerLargeModelFeignClient largeModelFeignClient = getNerLargeModelFeignClient(repository);
        NerFeignClient nerFeignClient = getNerFeignClient(repository);
        Mockito.when(largeModelFeignClient.chat(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any()))
            .thenReturn(buildLargeModelResponse(200, "{\"other\":[]}"));
        Mockito.when(nerFeignClient.extract(ArgumentMatchers.any())).thenReturn(buildLegacyPeopleResponse("张三"));

        List<String> peopleNameList = repository.parsePeopleNames("broker-a", "user-a", "欢迎张三办理业务", "trace-1");

        Assertions.assertEquals(List.of("张三"), peopleNameList);
    }

    /**
     * 验证新旧 NER 都失败时抛出异常并记录双失败指标。
     */
    @Test
    void testParsePeopleNamesShouldThrowWhenLargeModelAndLegacyBothFailed() {
        NerFeignRepositoryImpl repository = createRepository(2, 200);
        NerLargeModelFeignClient largeModelFeignClient = getNerLargeModelFeignClient(repository);
        NerFeignClient nerFeignClient = getNerFeignClient(repository);
        TtsMetricsCollector metricsCollector = getMetricsCollector(repository);
        Mockito.when(largeModelFeignClient.chat(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any()))
            .thenThrow(new IllegalStateException("large model failed"));
        Mockito.when(nerFeignClient.extract(ArgumentMatchers.any())).thenThrow(new IllegalStateException("legacy failed"));

        Assertions.assertThrows(
            NerFeignException.class,
            () -> repository.parsePeopleNames("broker-a", "user-a", "欢迎张三办理业务", "trace-1"));

        Mockito.verify(metricsCollector).recordNerLargeModelFailed("broker-a");
        Mockito.verify(metricsCollector).recordNerLargeModelAllFailed("broker-a");
    }

    /**
     * 验证模板达到上限时会先淘汰最近最久未使用的模板。
     */
    @Test
    void testMergeTemplateShouldEvictLeastRecentlyUsedTemplateBeforeInsertNewTemplate() {
        NerFeignRepositoryImpl repository = createRepository(2, 2);
        RedisOperator redisOperator = getRedisOperator(repository);
        ListAppender<ILoggingEvent> listAppender = attachTemplateLogAppender();
        long currentTimeMillis = System.currentTimeMillis();
        NerTemplateCacheEntity oldestTemplateEntity =
            buildTemplateEntity("\\Q欢迎\\E(.+?)\\Q办理业务\\E", 2, currentTimeMillis - 2000L);
        NerTemplateCacheEntity latestTemplateEntity =
            buildTemplateEntity("\\Q请\\E(.+?)\\Q确认身份\\E", 2, currentTimeMillis - 1000L);
        mockTemplateEntries(repository, List.of(oldestTemplateEntity, latestTemplateEntity));

        repository.mergeTemplate("broker-a", "您好王五开始视频见证", List.of("王五"), "trace-1");

        Mockito.verify(redisOperator).deleteHashFields(
            ArgumentMatchers.eq("ner_template_delete"),
            ArgumentMatchers.eq("tts:ner:template:broker-a"),
            ArgumentMatchers.argThat(arguments -> arguments.length == 1
                && oldestTemplateEntity.getTemplateId().equals(arguments[0])),
            ArgumentMatchers.eq(0L),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any());
        Mockito.verify(redisOperator).putHashValue(
            ArgumentMatchers.eq("ner_template_write"),
            ArgumentMatchers.eq("tts:ner:template:broker-a"),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any());
        Assertions.assertTrue(containsLog(listAppender, "NER模板缓存数量达到上限"));
        Assertions.assertTrue(containsLog(listAppender, "broker-a"));
        Assertions.assertTrue(containsLog(listAppender, oldestTemplateEntity.getTemplateId()));
        Assertions.assertTrue(containsLog(listAppender, "最近最少使用"));
        detachTemplateLogAppender(listAppender);
    }

    /**
     * 验证 Redis 组件返回降级值时模板匹配会按未命中处理。
     */
    @Test
    void testMatchAvailableTemplateShouldReturnEmptyWhenRedisUnavailable() {
        NerFeignRepositoryImpl repository = createRepository(2, 200);
        RedisOperator redisOperator = getRedisOperator(repository);
        Mockito.when(redisOperator.getHashEntries(
                ArgumentMatchers.eq("ner_template_read"),
                ArgumentMatchers.eq("tts:ner:template:broker-a"),
                ArgumentMatchers.anyMap(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any()))
            .thenReturn(Map.of());

        Optional<String> matchedTemplateOptional = repository.matchAvailableTemplate("broker-a", "欢迎张三办理业务", "trace-1");

        Assertions.assertTrue(matchedTemplateOptional.isEmpty());
    }

    /**
     * 创建仓储实现。
     *
     * @param matchThreshold 模板阈值
     * @param maxTemplateCountPerBroker 单券商模板上限
     * @return 仓储实现
     */
    private NerFeignRepositoryImpl createRepository(Integer matchThreshold, Integer maxTemplateCountPerBroker) {
        NerFeignRepositoryImpl repository = new NerFeignRepositoryImpl();
        NerTemplateCacheSupport templateCacheSupport = createTemplateCacheSupport(
            matchThreshold,
            maxTemplateCountPerBroker);
        ReflectionTestUtils.setField(repository, "nerFeignClient", Mockito.mock(NerFeignClient.class));
        ReflectionTestUtils.setField(repository, "nerLargeModelFeignClient", Mockito.mock(NerLargeModelFeignClient.class));
        ReflectionTestUtils.setField(repository, "speechNerProperties", buildSpeechNerProperties(matchThreshold, maxTemplateCountPerBroker));
        ReflectionTestUtils.setField(repository, "ttsMetricsCollector", Mockito.mock(TtsMetricsCollector.class));
        ReflectionTestUtils.setField(repository, "nerTemplateCacheSupport", templateCacheSupport);
        return repository;
    }

    /**
     * 创建模板缓存支持组件。
     *
     * @param matchThreshold 模板阈值
     * @param maxTemplateCountPerBroker 单券商模板上限
     * @return 模板缓存支持组件
     */
    private NerTemplateCacheSupport createTemplateCacheSupport(
        Integer matchThreshold,
        Integer maxTemplateCountPerBroker) {
        NerTemplateCacheSupport templateCacheSupport = new NerTemplateCacheSupport();
        ReflectionTestUtils.setField(templateCacheSupport, "redisOperator", Mockito.mock(RedisOperator.class));
        ReflectionTestUtils.setField(
            templateCacheSupport,
            "speechNerProperties",
            buildSpeechNerProperties(matchThreshold, maxTemplateCountPerBroker));
        ReflectionTestUtils.setField(templateCacheSupport, "speechTtsProperties", buildSpeechTtsProperties());
        ReflectionTestUtils.setField(templateCacheSupport, "ttsMetricsCollector", Mockito.mock(TtsMetricsCollector.class));
        return templateCacheSupport;
    }

    /**
     * 构建 NER 配置。
     *
     * @param matchThreshold 模板阈值
     * @param maxTemplateCountPerBroker 单券商模板上限
     * @return NER 配置
     */
    private SpeechNerProperties buildSpeechNerProperties(Integer matchThreshold, Integer maxTemplateCountPerBroker) {
        SpeechNerProperties speechNerProperties = new SpeechNerProperties();
        SpeechNerProperties.TemplateConfig templateConfig = new SpeechNerProperties.TemplateConfig();
        templateConfig.setMatchThreshold(matchThreshold);
        templateConfig.setTtlDays(5L);
        templateConfig.setMaxTemplateCountPerBroker(maxTemplateCountPerBroker);
        speechNerProperties.setTemplate(templateConfig);

        SpeechNerProperties.LargeModelConfig largeModelConfig = new SpeechNerProperties.LargeModelConfig();
        largeModelConfig.setMode("WORK_FLOW");
        largeModelConfig.setPipeName("3251");
        largeModelConfig.setRemoteIp("127.0.0.1");
        largeModelConfig.setRemoteSvc("agent-robot-engine-server");
        speechNerProperties.setLargeModel(largeModelConfig);
        return speechNerProperties;
    }

    /**
     * 构建 TTS 标签配置。
     *
     * @return TTS 配置
     */
    private SpeechTtsProperties buildSpeechTtsProperties() {
        SpeechTtsProperties speechTtsProperties = new SpeechTtsProperties();
        SpeechTtsProperties.NameTagConfig nameTagConfig = new SpeechTtsProperties.NameTagConfig();
        nameTagConfig.setNameTagStartTag("<say-as interpret-as=\"name\">");
        nameTagConfig.setNameTagEndTag("</say-as>");
        speechTtsProperties.setNameTag(nameTagConfig);
        SpeechTtsProperties.SsmlConfig ssmlConfig = new SpeechTtsProperties.SsmlConfig();
        ssmlConfig.setSpeakStartTag("<speak>");
        ssmlConfig.setSpeakEndTag("</speak>");
        speechTtsProperties.setSsml(ssmlConfig);
        return speechTtsProperties;
    }

    /**
     * 模拟模板缓存数据。
     *
     * @param repository 仓储实现
     * @param templateEntityList 模板列表
     */
    private void mockTemplateEntries(NerFeignRepositoryImpl repository, List<NerTemplateCacheEntity> templateEntityList) {
        Map<String, String> templateEntryMap = templateEntityList.stream()
            .collect(LinkedHashMap::new, (templateMap, templateEntity) ->
                templateMap.put(templateEntity.getTemplateId(), JsonUtil.toJson(templateEntity)), LinkedHashMap::putAll);
        Mockito.when(getRedisOperator(repository).getHashEntries(
                ArgumentMatchers.eq("ner_template_read"),
                ArgumentMatchers.eq("tts:ner:template:broker-a"),
                ArgumentMatchers.anyMap(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any()))
            .thenReturn(templateEntryMap);
        Mockito.when(getRedisOperator(repository).putHashValue(
                ArgumentMatchers.eq("ner_template_write"),
                ArgumentMatchers.eq("tts:ner:template:broker-a"),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any()))
            .thenReturn(true);
        Mockito.when(getRedisOperator(repository).deleteHashFields(
                ArgumentMatchers.eq("ner_template_delete"),
                ArgumentMatchers.eq("tts:ner:template:broker-a"),
                ArgumentMatchers.any(Object[].class),
                ArgumentMatchers.eq(0L),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any()))
            .thenReturn(1L);
    }

    /**
     * 构建模板实体。
     *
     * @param templatePattern 模板正则
     * @param useCount 命中次数
     * @param lastMatchedAt 最近命中时间
     * @return 模板实体
     */
    private NerTemplateCacheEntity buildTemplateEntity(String templatePattern, Integer useCount, Long lastMatchedAt) {
        return NerTemplateCacheEntity.builder()
            .templateId(org.springframework.util.DigestUtils.md5DigestAsHex(templatePattern.getBytes()))
            .templatePattern(templatePattern)
            .useCount(useCount)
            .lastMatchedAt(lastMatchedAt)
            .build();
    }

    /**
     * 构建新版大模型响应。
     *
     * @param statusCode 状态码
     * @param text 固定 JSON 字符串
     * @return 响应 DTO
     */
    private NerLargeModelRespDTO buildLargeModelResponse(Integer statusCode, String text) {
        return NerLargeModelRespDTO.builder()
            .statusCode(statusCode)
            .statusMsg("ok")
            .response(NerLargeModelRespDTO.Response.builder().text(text).build())
            .build();
    }

    /**
     * 构建旧版 NER 人名响应。
     *
     * @param peopleName 人名
     * @return 响应 Map
     */
    private Map<String, Object> buildLegacyPeopleResponse(String peopleName) {
        return Map.of(
            CommonConstant.RESULTS_KEY,
            List.of(Map.of(CommonConstant.PEOPLE_NAME_KEY, List.of(Map.of(CommonConstant.TEXT_KEY, peopleName)))));
    }

    /**
     * 获取旧版 NER Feign 客户端。
     *
     * @param repository 仓储实现
     * @return 旧版 NER Feign 客户端
     */
    private NerFeignClient getNerFeignClient(NerFeignRepositoryImpl repository) {
        return (NerFeignClient) ReflectionTestUtils.getField(repository, "nerFeignClient");
    }

    /**
     * 获取新版 NER Feign 客户端。
     *
     * @param repository 仓储实现
     * @return 新版 NER Feign 客户端
     */
    private NerLargeModelFeignClient getNerLargeModelFeignClient(NerFeignRepositoryImpl repository) {
        return (NerLargeModelFeignClient) ReflectionTestUtils.getField(repository, "nerLargeModelFeignClient");
    }

    /**
     * 获取 Redis 访问组件。
     *
     * @param repository 仓储实现
     * @return Redis 访问组件
     */
    private RedisOperator getRedisOperator(NerFeignRepositoryImpl repository) {
        NerTemplateCacheSupport templateCacheSupport = getTemplateCacheSupport(repository);
        return (RedisOperator) ReflectionTestUtils.getField(templateCacheSupport, "redisOperator");
    }

    /**
     * 获取指标收集器。
     *
     * @param repository 仓储实现
     * @return 指标收集器
     */
    private TtsMetricsCollector getMetricsCollector(NerFeignRepositoryImpl repository) {
        return (TtsMetricsCollector) ReflectionTestUtils.getField(repository, "ttsMetricsCollector");
    }

    /**
     * 获取模板缓存支持组件。
     *
     * @param repository 仓储实现
     * @return 模板缓存支持组件
     */
    private NerTemplateCacheSupport getTemplateCacheSupport(NerFeignRepositoryImpl repository) {
        return (NerTemplateCacheSupport) ReflectionTestUtils.getField(repository, "nerTemplateCacheSupport");
    }

    /**
     * 挂载模板缓存日志收集器。
     *
     * @return 日志收集器
     */
    private ListAppender<ILoggingEvent> attachTemplateLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(NerTemplateCacheSupport.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        return listAppender;
    }

    /**
     * 释放模板缓存日志收集器。
     *
     * @param listAppender 日志收集器
     */
    private void detachTemplateLogAppender(ListAppender<ILoggingEvent> listAppender) {
        Logger logger = (Logger) LoggerFactory.getLogger(NerTemplateCacheSupport.class);
        logger.detachAppender(listAppender);
    }

    /**
     * 鎸傝浇 NER 浠撳偍鏃ュ織鏀堕泦鍣ㄣ€?
     *
     * @return 鏃ュ織鏀堕泦鍣?
     */
    private ListAppender<ILoggingEvent> attachNerLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(NerFeignRepositoryImpl.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        return listAppender;
    }

    /**
     * 閲婃斁 NER 浠撳偍鏃ュ織鏀堕泦鍣ㄣ€?
     *
     * @param listAppender 鏃ュ織鏀堕泦鍣?
     */
    private void detachNerLogAppender(ListAppender<ILoggingEvent> listAppender) {
        Logger logger = (Logger) LoggerFactory.getLogger(NerFeignRepositoryImpl.class);
        logger.detachAppender(listAppender);
    }

    /**
     * 判断日志中是否包含目标文本。
     *
     * @param listAppender 日志收集器
     * @param text 目标文本
     * @return 是否包含
     */
    private boolean containsLog(ListAppender<ILoggingEvent> listAppender, String text) {
        return listAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .anyMatch(message -> message.contains(text));
    }
}
