package com.tts.speech.deploy.modules.tts.domain.service.impl;

import com.tts.speech.deploy.modules.tts.domain.repository.TtsCacheRepository;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * TtsCacheDomainServiceImpl 单元测试。
 *
 * @author yangchen5
 * @since 2026-03-19 15:30:00
 */
class TtsCacheDomainServiceImplTest {

    /**
     * 验证仅返回删除失败的缓存键。
     */
    @Test
    void testFilterDeleteFailedKeysShouldReturnFailedKeysOnly() {
        TtsCacheDomainServiceImpl domainService = new TtsCacheDomainServiceImpl();
        TtsCacheRepository ttsCacheRepository = Mockito.mock(TtsCacheRepository.class);
        ReflectionTestUtils.setField(domainService, "ttsCacheRepository", ttsCacheRepository);
        Mockito.when(ttsCacheRepository.deleteKey("key-1")).thenReturn(true);
        Mockito.when(ttsCacheRepository.deleteKey("key-2")).thenReturn(false);
        Mockito.when(ttsCacheRepository.deleteKey("key-3")).thenReturn(false);

        List<String> failedKeyList = domainService.filterDeleteFailedKeys(List.of("key-1", "key-2", "key-3"));

        Assertions.assertEquals(List.of("key-2", "key-3"), failedKeyList);
    }

    /**
     * 验证查询缓存值时会透传仓储结果。
     */
    @Test
    void testGetCacheValueShouldDelegateRepository() {
        TtsCacheDomainServiceImpl domainService = new TtsCacheDomainServiceImpl();
        TtsCacheRepository ttsCacheRepository = Mockito.mock(TtsCacheRepository.class);
        ReflectionTestUtils.setField(domainService, "ttsCacheRepository", ttsCacheRepository);
        Mockito.when(ttsCacheRepository.getCacheValue("cache-key")).thenReturn("{\"value\":1}");

        Object cacheValue = domainService.getCacheValue("cache-key");

        Assertions.assertEquals("{\"value\":1}", cacheValue);
    }
}
