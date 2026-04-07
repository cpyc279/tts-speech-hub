package com.tts.speech.deploy.app.manager;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tts.speech.deploy.app.dto.AdminCacheDeleteDataDTO;
import com.tts.speech.deploy.interfaces.rest.req.AdminCacheDeleteReq;

/**
 * 后台缓存管理编排接口。
 *
 * @author yangchen5
 * @since 2026-03-06 20:05:00
 */
public interface CacheAdminManager {

    /**
     * 批量删除缓存键。
     *
     * @param adminToken 后台固定 token
     * @param request 删除请求
     * @return 删除结果
     */
    AdminCacheDeleteDataDTO deleteCacheKeys(String adminToken, AdminCacheDeleteReq request);

    /**
     * 根据缓存 key 查询缓存 value。
     *
     * @param adminToken 后台固定 token
     * @param key 缓存 key
     * @return JSON 对象形式的缓存值
     */
    ObjectNode getCacheValue(String adminToken, String key);
}
