package com.tts.speech.deploy.modules.tts.infrastructure.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 券商维度缓存淘汰支持类。
 *
 * @author yangchen5
 * @since 2026-03-23 15:30:00
 */
public final class BrokerScopedCacheEvictionSupport {

    /**
     * 私有构造器。
     */
    private BrokerScopedCacheEvictionSupport() {
    }

    /**
     * 收集已过期的条目 ID 列表。
     *
     * @param entryMap 条目映射
     * @param expiredPredicate 过期断言
     * @param entryIdFunction 条目 ID 提取函数
     * @param <T> 条目类型
     * @return 已过期条目 ID 列表
     */
    public static <T> List<String> collectExpiredEntryIdList(
        Map<String, T> entryMap,
        Predicate<T> expiredPredicate,
        Function<T, String> entryIdFunction) {
        List<String> expiredEntryIdList = new ArrayList<>();

        // 逐条执行过期判断，统一收集待删除的条目 ID。
        for (T entry : entryMap.values()) {
            if (expiredPredicate.test(entry)) {
                expiredEntryIdList.add(entryIdFunction.apply(entry));
            }
        }
        return expiredEntryIdList;
    }

    /**
     * 选择最近最久未使用的条目。
     *
     * @param entryCollection 条目集合
     * @param accessTimeFunction 最近访问时间提取函数
     * @param entryIdFunction 条目 ID 提取函数
     * @param <T> 条目类型
     * @return 最近最久未使用的条目
     */
    public static <T> T findLeastRecentlyUsedEntry(
        Collection<T> entryCollection,
        Function<T, Long> accessTimeFunction,
        Function<T, String> entryIdFunction) {
        Comparator<T> comparator = Comparator
            .comparing(accessTimeFunction, Comparator.nullsFirst(Long::compareTo))
            .thenComparing(entryIdFunction, Comparator.nullsFirst(String::compareTo));

        // 按最近访问时间升序选择第一条，得到最久未使用的缓存。
        return entryCollection.stream().sorted(comparator).findFirst().orElse(null);
    }
}
