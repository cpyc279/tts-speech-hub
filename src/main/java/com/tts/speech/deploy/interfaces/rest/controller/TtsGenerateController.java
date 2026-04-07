package com.tts.speech.deploy.interfaces.rest.controller;

import com.tts.speech.deploy.app.converter.TtsApiConverter;
import com.tts.speech.deploy.app.dto.ApiResponseDTO;
import com.tts.speech.deploy.app.dto.TtsGenerateDataDTO;
import com.tts.speech.deploy.app.manager.TtsManager;
import com.tts.speech.deploy.common.constant.StringPool;
import com.tts.speech.deploy.common.util.TraceIdUtil;
import com.tts.speech.deploy.interfaces.rest.req.TtsGenerateReq;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsRequestEntity;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.StringJoiner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TTS接口控制器。
 *
 * @author yangchen5
 * @since 2026-03-06 19:38:00
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/tts/v1")
public class TtsGenerateController {

    @Resource
    private TtsManager ttsManager;

    @Resource
    private TtsApiConverter ttsApiConverter;

    /**
     * 批量生成TTS音频。
     *
     * ### 签名算法
     * - 算法：`HMAC-SHA256`
     * - 编码：`UTF-8`
     * - 输出格式：十六进制小写字符串
     *
     * 待签名字字符串格式：
     * ```text
     * {broker_id}_{user_id}_{timestamp}
     * ```
     *
     * 说明：
     * - 使用下划线 `_` 连接 `broker_id`、`user_id`、`timestamp`
     * - 当 `broker_id` 或 `user_id` 为空时，仍需保留下划线分隔位
     * - 不包含请求方法、接口路径、域名、端口和 query 参数
     *
     * ### 时间戳规则
     * - `timestamp` 必须为当前时间附近的秒级时间戳
     * - 默认允许误差窗口为 `5` 分钟
     *
     * @param request 请求体
     * @return 统一响应
     */
    @PostMapping("/generate")
    public ApiResponseDTO<TtsGenerateDataDTO> generate(@Valid @RequestBody TtsGenerateReq request) {
        // 先按用户维度获取本次请求 traceId，确保入口日志和后续链路共用同一标识。
        String traceId = ttsManager.getTraceId(request.getUserId());
        // 绑定 MDC 后再打印入口日志，便于统一追踪本次请求。
        TraceIdUtil.bindTraceId(traceId);
        // 仅输出有值的请求参数，避免无效字段污染日志。
        log.info("TTS生成接口接收请求，{}", buildRequestLogContent(traceId, request));
        // 将对外请求对象转换为领域对象，并补齐前面已经生成好的 traceId。
        TtsRequestEntity requestEntity = ttsApiConverter.toEntity(request);
        requestEntity.setTraceId(traceId);
        // 交由 manager 继续执行业务编排。
        TtsGenerateDataDTO ttsGenerateDataDTO = ttsManager.synthesize(requestEntity);
        return ApiResponseDTO.success(ttsGenerateDataDTO);
    }

    /**
     * 构建请求日志内容。
     *
     * @param traceId 链路标识
     * @param request 请求参数
     * @return 日志内容
     */
    private static String buildRequestLogContent(String traceId, TtsGenerateReq request) {
        StringJoiner joiner = new StringJoiner(StringPool.COMMA + StringPool.SPACE);
        joiner.add("traceId=" + traceId);
        if (StringUtils.hasText(request.getSign())) {
            joiner.add("sign=" + request.getSign());
        }
        if (StringUtils.hasText(request.getTimestamp())) {
            joiner.add("timestamp=" + request.getTimestamp());
        }
        if (StringUtils.hasText(request.getBrokerId())) {
            joiner.add("brokerId=" + request.getBrokerId());
        }
        if (StringUtils.hasText(request.getUserId())) {
            joiner.add("userId=" + request.getUserId());
        }
        if (StringUtils.hasText(request.getBusinessCode())) {
            joiner.add("businessCode=" + request.getBusinessCode());
        }
        if (StringUtils.hasText(request.getDeviceModel())) {
            joiner.add("deviceModel=" + request.getDeviceModel());
        }
        if (StringUtils.hasText(request.getSystemVersion())) {
            joiner.add("systemVersion=" + request.getSystemVersion());
        }
        if (!CollectionUtils.isEmpty(request.getRawTextList())) {
            joiner.add("rawTextList=" + request.getRawTextList());
        }
        return joiner.toString();
    }
}
