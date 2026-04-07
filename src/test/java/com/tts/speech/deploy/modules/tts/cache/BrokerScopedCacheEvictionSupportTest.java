package com.tts.speech.deploy.modules.tts.cache;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tts.speech.deploy.modules.tts.domain.entity.TtsAudioCacheIndexEntity;
import com.tts.speech.deploy.modules.tts.infrastructure.cache.BrokerScopedCacheEvictionSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * BrokerScopedCacheEvictionSupport 单元测试。
 *
 * @author yangchen5
 * @since 2026-03-23 15:30:00
 */
class BrokerScopedCacheEvictionSupportTest {

    /**
     * 验证可以收集过期条目。
     */
    @Test
    void testCollectExpiredEntryIdListShouldReturnExpiredIds() {
        Map<String, TtsAudioCacheIndexEntity> entryMap = new LinkedHashMap<>();
        entryMap.put("cache-a", TtsAudioCacheIndexEntity.builder().cacheKey("cache-a").expireAt(1L).build());
        entryMap.put("cache-b", TtsAudioCacheIndexEntity.builder().cacheKey("cache-b").expireAt(10L).build());

        List<String> expiredEntryIdList = BrokerScopedCacheEvictionSupport.collectExpiredEntryIdList(
            entryMap,
            entry -> entry.getExpireAt() != null && entry.getExpireAt() <= 5L,
            TtsAudioCacheIndexEntity::getCacheKey);

        Assertions.assertEquals(List.of("cache-a"), expiredEntryIdList);
    }

    /**
     * 验证可以找到最近最久未使用的条目。
     */
    @Test
    void testFindLeastRecentlyUsedEntryShouldReturnOldestEntry() {
        TtsAudioCacheIndexEntity leastRecentlyUsedEntry = BrokerScopedCacheEvictionSupport.findLeastRecentlyUsedEntry(
            List.of(
                TtsAudioCacheIndexEntity.builder().cacheKey("cache-a").lastAccessedAt(20L).build(),
                TtsAudioCacheIndexEntity.builder().cacheKey("cache-b").lastAccessedAt(10L).build()),
            TtsAudioCacheIndexEntity::getLastAccessedAt,
            TtsAudioCacheIndexEntity::getCacheKey);

        Assertions.assertNotNull(leastRecentlyUsedEntry);
        Assertions.assertEquals("cache-b", leastRecentlyUsedEntry.getCacheKey());
    }
}
