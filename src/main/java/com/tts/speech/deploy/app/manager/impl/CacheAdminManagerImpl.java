package com.tts.speech.deploy.app.manager.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tts.speech.deploy.app.dto.AdminCacheDeleteDataDTO;
import com.tts.speech.deploy.app.manager.CacheAdminManager;
import com.tts.speech.deploy.app.shared.auth.SignAuthService;
import com.tts.speech.deploy.common.util.JsonUtil;
import com.tts.speech.deploy.interfaces.rest.req.AdminCacheDeleteReq;
import com.tts.speech.deploy.modules.tts.domain.service.TtsCacheDomainService;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 后台缓存管理编排实现。
 *
 * @author yangchen5
 * @since 2026-03-06 20:05:00
 */
@Slf4j
@Component
public class CacheAdminManagerImpl implements CacheAdminManager {

    @Resource
    private TtsCacheDomainService ttsCacheDomainService;

    @Resource
    private SignAuthService signAuthService;

    /**
     * 批量删除缓存键。
     *
     * @param adminToken 后台固定 token
     * @param request 删除请求
     * @return 删除结果
     */
    @Override
    public AdminCacheDeleteDataDTO deleteCacheKeys(String adminToken, AdminCacheDeleteReq request) {
        // 先在编排层统一完成后台 token 鉴权，保持 controller 仅负责参数接收。
        signAuthService.validateAdminAuth(adminToken);
        // 逐条删除缓存键，并收集删除失败的键列表。
        List<String> failKeyList = ttsCacheDomainService.filterDeleteFailedKeys(request.getKeyList());
        // 成功数量由请求总数减去失败数量直接计算得出。
        long successCount = (long) request.getKeyList().size() - failKeyList.size();
        return AdminCacheDeleteDataDTO.builder()
            .successCount(successCount)
            .failKeyList(failKeyList)
            .build();
    }

    /**
     * 根据缓存 key 查询缓存 value。
     *
     * @param adminToken 后台固定 token
     * @param key 缓存 key
     * @return JSON 对象形式的缓存值
     */
    @Override
    public ObjectNode getCacheValue(String adminToken, String key) {
        // 查询缓存前先完成后台 token 鉴权，避免未鉴权请求直接进入领域服务。
        signAuthService.validateAdminAuth(adminToken);
        // 先从领域层读取原始缓存值，再统一转换为 JSON 对象输出。
        Object cacheValue = ttsCacheDomainService.getCacheValue(key);
        JsonNode cacheValueNode = buildCacheValueNode(cacheValue);
        if (cacheValueNode instanceof ObjectNode) {
            return (ObjectNode) cacheValueNode;
        }
        return buildWrappedValueNode(cacheValueNode);
    }

    /**
     * 构建缓存值 JSON 节点。
     *
     * @param cacheValue 缓存值
     * @return JSON 节点
     */
    private static JsonNode buildCacheValueNode(Object cacheValue) {
        if (cacheValue == null) {
            return NullNode.getInstance();
        }
        // Redis Hash 结构直接展开为 JSON 对象，便于后台查看字段内容。
        if (cacheValue instanceof Map) {
            Map<?, ?> cacheValueMap = (Map<?, ?>) cacheValue;
            return buildObjectNodeFromMap(cacheValueMap);
        }
        // 字符串缓存再区分是否为 JSON 文本。
        if (cacheValue instanceof String) {
            String cacheValueText = (String) cacheValue;
            return buildJsonNodeFromText(cacheValueText);
        }
        return JsonUtil.valueToTree(cacheValue);
    }

    /**
     * 解析字符串缓存值。
     *
     * @param cacheValueText 字符串缓存值
     * @return JSON 节点
     */
    private static JsonNode buildJsonNodeFromText(String cacheValueText) {
        if (!StringUtils.hasText(cacheValueText)) {
            return NullNode.getInstance();
        }
        // 普通文本不做 JSON 解析，直接包装成文本节点返回。
        if (!looksLikeJsonText(cacheValueText)) {
            return JsonUtil.valueToTree(cacheValueText);
        }
        try {
            // 结构化字符串按 JSON 解析，保留对象或数组结构。
            return JsonUtil.readTree(cacheValueText);
        } catch (IllegalStateException exception) {
            log.warn("缓存值解析异常，key={}", cacheValueText, exception);
            // 解析失败时降级为普通文本节点，避免后台查询接口报错。
            return JsonUtil.valueToTree(cacheValueText);
        }
    }

    /**
     * 将哈希结构缓存值转换为 JSON 对象。
     *
     * @param cacheValueMap 哈希结构缓存值
     * @return JSON 对象
     */
    private static ObjectNode buildObjectNodeFromMap(Map<?, ?> cacheValueMap) {
        ObjectNode objectNode = JsonUtil.createObjectNode();
        for (Map.Entry<?, ?> entry : cacheValueMap.entrySet()) {
            // Hash 的 value 仍可能是 JSON 字符串，这里继续递归转换。
            objectNode.set(String.valueOf(entry.getKey()), buildCacheValueNode(entry.getValue()));
        }
        return objectNode;
    }

    /**
     * 包装非对象缓存值。
     *
     * @param cacheValueNode 缓存值节点
     * @return JSON 对象
     */
    private static ObjectNode buildWrappedValueNode(JsonNode cacheValueNode) {
        ObjectNode objectNode = JsonUtil.createObjectNode();
        objectNode.set("value", cacheValueNode);
        return objectNode;
    }

    /**
     * 判断字符串是否可能为 JSON 文本。
     *
     * @param cacheValueText 字符串缓存值
     * @return 是否可能为 JSON 文本
     */
    private static boolean looksLikeJsonText(String cacheValueText) {
        String trimmedText = cacheValueText.trim();
        return trimmedText.startsWith("{") || trimmedText.startsWith("[");
    }
}
