package com.tts.speech.deploy.interfaces.rest.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tts.speech.deploy.app.dto.AdminCacheDeleteDataDTO;
import com.tts.speech.deploy.app.dto.ApiResponseDTO;
import com.tts.speech.deploy.app.manager.CacheAdminManager;
import com.tts.speech.deploy.interfaces.rest.req.AdminCacheDeleteReq;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台缓存管理控制器。
 *
 * @author yangchen5
 * @since 2026-03-06 19:38:00
 */
@Validated
@RestController
@RequestMapping("/admin/cache/v1")
public class AdminCacheController {

    private static final String ADMIN_TOKEN_HEADER_NAME = "admin-token";

    @Resource
    private CacheAdminManager cacheAdminManager;

    /**
     * 删除缓存键。
     *
     * @param adminToken 后台固定 token
     * @param request 请求体
     * @return 删除结果
     */
    @PostMapping("/delete")
    public ApiResponseDTO<AdminCacheDeleteDataDTO> delete(
        @RequestHeader(name = ADMIN_TOKEN_HEADER_NAME, required = false) String adminToken,
        @Valid @RequestBody AdminCacheDeleteReq request) {
        // controller 只保留入参接收职责，后台鉴权和业务编排统一下沉到 manager 层。
        return ApiResponseDTO.success(cacheAdminManager.deleteCacheKeys(adminToken, request));
    }

    /**
     * 根据缓存 key 查询缓存 value。
     *
     * @param adminToken 后台固定 token
     * @param key 缓存 key
     * @return 缓存 value 对应的 JSON 对象
     */
    @GetMapping("/value")
    public ApiResponseDTO<ObjectNode> getValue(
        @RequestHeader(name = ADMIN_TOKEN_HEADER_NAME, required = false) String adminToken,
        @NotBlank(message = "key 不能为空") @RequestParam("key") String key) {
        // 查询接口同样只负责接收参数，避免 controller 跨层直接调用鉴权服务。
        return ApiResponseDTO.success(cacheAdminManager.getCacheValue(adminToken, key));
    }
}
