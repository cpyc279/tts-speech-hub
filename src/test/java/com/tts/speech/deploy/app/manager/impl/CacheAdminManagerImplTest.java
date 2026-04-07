package com.tts.speech.deploy.app.manager.impl;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tts.speech.deploy.app.shared.auth.SignAuthService;
import com.tts.speech.deploy.modules.tts.domain.service.TtsCacheDomainService;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * CacheAdminManagerImpl 单元测试。
 *
 * @author yangchen5
 * @since 2026-03-19 16:50:00
 */
class CacheAdminManagerImplTest {

    /**
     * 验证 JSON 字符串缓存值会直接返回对象结构。
     */
    @Test
    void testGetCacheValueShouldReturnJsonObjectWhenCacheValueIsJsonObject() {
        CacheAdminManagerImpl manager = createManager();
        TtsCacheDomainService ttsCacheDomainService = getTtsCacheDomainService(manager);
        Mockito.when(ttsCacheDomainService.getCacheValue("cache-key")).thenReturn("{\"audioUrl\":\"demo-url\"}");

        ObjectNode objectNode = manager.getCacheValue("admin-token", "cache-key");

        Assertions.assertEquals("demo-url", objectNode.get("audioUrl").asText());
    }

    /**
     * 验证普通字符串缓存值会被包装到 value 字段中。
     */
    @Test
    void testGetCacheValueShouldWrapPlainTextWhenCacheValueIsString() {
        CacheAdminManagerImpl manager = createManager();
        TtsCacheDomainService ttsCacheDomainService = getTtsCacheDomainService(manager);
        Mockito.when(ttsCacheDomainService.getCacheValue("cache-key")).thenReturn("plain-text");

        ObjectNode objectNode = manager.getCacheValue("admin-token", "cache-key");

        Assertions.assertEquals("plain-text", objectNode.get("value").asText());
    }

    /**
     * 验证 Hash 类型缓存值中的 JSON 字符串会继续解析。
     */
    @Test
    void testGetCacheValueShouldParseNestedJsonWhenCacheValueIsMap() {
        CacheAdminManagerImpl manager = createManager();
        TtsCacheDomainService ttsCacheDomainService = getTtsCacheDomainService(manager);
        Mockito.when(ttsCacheDomainService.getCacheValue("inspect-key")).thenReturn(Map.of("broker-a", "[1,2]"));

        ObjectNode objectNode = manager.getCacheValue("admin-token", "inspect-key");

        Assertions.assertTrue(objectNode.get("broker-a").isArray());
        Assertions.assertEquals(2, objectNode.get("broker-a").size());
    }

    /**
     * 创建后台缓存管理器。
     *
     * @return 后台缓存管理器
     */
    private CacheAdminManagerImpl createManager() {
        CacheAdminManagerImpl manager = new CacheAdminManagerImpl();
        ReflectionTestUtils.setField(manager, "ttsCacheDomainService", Mockito.mock(TtsCacheDomainService.class));
        ReflectionTestUtils.setField(manager, "signAuthService", Mockito.mock(SignAuthService.class));
        return manager;
    }

    /**
     * 获取缓存领域服务。
     *
     * @param manager 后台缓存管理器
     * @return 缓存领域服务
     */
    private TtsCacheDomainService getTtsCacheDomainService(CacheAdminManagerImpl manager) {
        return (TtsCacheDomainService) ReflectionTestUtils.getField(manager, "ttsCacheDomainService");
    }
}
