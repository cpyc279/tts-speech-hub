package com.tts.speech.deploy.app.manager.impl;

import com.tts.speech.deploy.app.converter.TtsResponseConverter;
import com.tts.speech.deploy.app.dto.TtsGenerateDataDTO;
import com.tts.speech.deploy.app.shared.auth.SignAuthService;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsRequestEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsResponseEntity;
import com.tts.speech.deploy.modules.tts.domain.repository.TtsCacheRepository;
import com.tts.speech.deploy.modules.tts.domain.service.TtsDomainService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * TtsManagerImpl 单元测试。
 *
 * @author yangchen5
 * @since 2026-03-28 15:40:00
 */
class TtsManagerImplTest {

    /**
     * 验证获取 traceId 方法会委托缓存仓储。
     */
    @Test
    void testGetTraceIdShouldDelegateToCacheRepository() {
        TtsManagerImpl manager = createManager();
        TtsCacheRepository ttsCacheRepository = getTtsCacheRepository(manager);
        Mockito.when(ttsCacheRepository.getOrCreateTraceId("user-a")).thenReturn("trace-1");

        String traceId = manager.getTraceId("user-a");

        Assertions.assertEquals("trace-1", traceId);
    }

    /**
     * 验证 manager 编排阶段不再绑定 traceId。
     */
    @Test
    void testSynthesizeShouldNotBindTraceIdInManager() {
        TtsManagerImpl manager = createManager();
        TtsDomainService ttsDomainService = getTtsDomainService(manager);
        TtsResponseConverter ttsResponseConverter = getTtsResponseConverter(manager);
        TtsRequestEntity requestEntity = TtsRequestEntity.builder()
            .userId("user-a")
            .traceId("trace-1")
            .build();
        TtsResponseEntity responseEntity = TtsResponseEntity.builder().build();
        TtsGenerateDataDTO dataDTO = TtsGenerateDataDTO.builder().build();
        Mockito.when(ttsDomainService.synthesize(requestEntity)).thenReturn(responseEntity);
        Mockito.when(ttsResponseConverter.toDataDTO(responseEntity)).thenReturn(dataDTO);

        TtsGenerateDataDTO result = manager.synthesize(requestEntity);

        Assertions.assertSame(dataDTO, result);
        Mockito.verify(getSignAuthService(manager)).validateTtsSign(requestEntity);
        Mockito.verify(getTtsCacheRepository(manager), Mockito.never()).getOrCreateTraceId(Mockito.anyString());
    }

    /**
     * 创建 manager。
     *
     * @return manager
     */
    private TtsManagerImpl createManager() {
        TtsManagerImpl manager = new TtsManagerImpl();
        ReflectionTestUtils.setField(manager, "ttsDomainService", Mockito.mock(TtsDomainService.class));
        ReflectionTestUtils.setField(manager, "ttsResponseConverter", Mockito.mock(TtsResponseConverter.class));
        ReflectionTestUtils.setField(manager, "signAuthService", Mockito.mock(SignAuthService.class));
        ReflectionTestUtils.setField(manager, "ttsCacheRepository", Mockito.mock(TtsCacheRepository.class));
        return manager;
    }

    /**
     * 获取缓存仓储。
     *
     * @param manager manager
     * @return 缓存仓储
     */
    private TtsCacheRepository getTtsCacheRepository(TtsManagerImpl manager) {
        return (TtsCacheRepository) ReflectionTestUtils.getField(manager, "ttsCacheRepository");
    }

    /**
     * 获取领域服务。
     *
     * @param manager manager
     * @return 领域服务
     */
    private TtsDomainService getTtsDomainService(TtsManagerImpl manager) {
        return (TtsDomainService) ReflectionTestUtils.getField(manager, "ttsDomainService");
    }

    /**
     * 获取响应转换器。
     *
     * @param manager manager
     * @return 响应转换器
     */
    private TtsResponseConverter getTtsResponseConverter(TtsManagerImpl manager) {
        return (TtsResponseConverter) ReflectionTestUtils.getField(manager, "ttsResponseConverter");
    }

    /**
     * 获取签名校验服务。
     *
     * @param manager manager
     * @return 签名校验服务
     */
    private SignAuthService getSignAuthService(TtsManagerImpl manager) {
        return (SignAuthService) ReflectionTestUtils.getField(manager, "signAuthService");
    }
}
