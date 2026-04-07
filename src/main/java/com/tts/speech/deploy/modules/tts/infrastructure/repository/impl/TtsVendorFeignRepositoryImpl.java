package com.tts.speech.deploy.modules.tts.infrastructure.repository.impl;

import com.tts.speech.deploy.common.enums.ErrorCodeEnum;
import com.tts.speech.deploy.common.enums.TtsEndpointEnum;
import com.tts.speech.deploy.common.exception.BizException;
import com.tts.speech.deploy.common.util.JsonUtil;
import com.tts.speech.deploy.common.util.SsmlUtil;
import com.tts.speech.deploy.modules.tts.domain.constant.TtsVendorConstant;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsPreparedTextEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsVendorResponseEntity;
import com.tts.speech.deploy.modules.tts.domain.repository.TtsVendorAuthRepository;
import com.tts.speech.deploy.modules.tts.domain.repository.TtsVendorFeignRepository;
import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechTtsProperties;
import com.tts.speech.deploy.modules.tts.infrastructure.external.dto.ThsLargeTtsReqDTO;
import com.tts.speech.deploy.modules.tts.infrastructure.external.dto.ThsLargeTtsRespDTO;
import com.tts.speech.deploy.modules.tts.infrastructure.external.dto.ThsSmallTtsRespDTO;
import com.tts.speech.deploy.modules.tts.infrastructure.external.feign.ThsTtsLargeFeignClient;
import com.tts.speech.deploy.modules.tts.infrastructure.external.feign.ThsTtsSmallFeignClient;
import jakarta.annotation.Resource;
import java.util.EnumMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * TTS 厂商仓储实现。
 *
 * @author yangchen5
 * @since 2026-03-06 20:20:00
 */
@Slf4j
@Service
public class TtsVendorFeignRepositoryImpl implements TtsVendorFeignRepository {

    @Resource
    private ThsTtsSmallFeignClient thsTtsSmallFeignClient;

    @Resource
    private ThsTtsLargeFeignClient thsTtsLargeFeignClient;

    @Resource
    private TtsVendorAuthRepository ttsVendorAuthRepository;

    @Resource
    private SpeechTtsProperties speechTtsProperties;

    /**
     * 按选定厂商端点合成单条文本。
     *
     * @param endpointEnum 端点枚举
     * @param modelCode 模型编码
     * @param preparedTextEntity 预处理文本实体
     * @param traceId 链路追踪标识
     * @return 厂商响应
     */
    @Override
    public TtsVendorResponseEntity synthesize(
        TtsEndpointEnum endpointEnum,
        String modelCode,
        TtsPreparedTextEntity preparedTextEntity,
        String traceId) {
        // 按端点选择实际调用策略，避免在领域层分散端点判断逻辑。
        Map<TtsEndpointEnum, TtsInvoker> invokerMap = buildInvokerMap();
        TtsInvoker ttsInvoker = invokerMap.get(endpointEnum);
        if (ttsInvoker == null) {
            throw new BizException(ErrorCodeEnum.TTS_ROUTE_MISSING);
        }
        // 把预处理文本和 traceId 一起下传，便于端点内部决定实际请求文本并打印日志。
        return ttsInvoker.invoke(modelCode, preparedTextEntity, traceId);
    }

    /**
     * 构建端点调用器映射。
     *
     * @return 调用器映射
     */
    private Map<TtsEndpointEnum, TtsInvoker> buildInvokerMap() {
        Map<TtsEndpointEnum, TtsInvoker> invokerMap = new EnumMap<>(TtsEndpointEnum.class);
        // 小模型与大模型分别绑定独立调用逻辑。
        invokerMap.put(TtsEndpointEnum.SMALL, this::invokeSmallModel);
        invokerMap.put(TtsEndpointEnum.LARGE, this::invokeLargeModel);
        return invokerMap;
    }

    /**
     * 调用小模型端点。
     *
     * @param modelCode 模型编码
     * @param preparedTextEntity 预处理文本实体
     * @param traceId 链路追踪标识
     * @return 厂商响应
     */
    private TtsVendorResponseEntity invokeSmallModel(
        String modelCode,
        TtsPreparedTextEntity preparedTextEntity,
        String traceId) {
        String synthesizeText = preparedTextEntity.getFinalText();
        // 小模型继续使用最终文本。
        Map<String, String> formMap = ttsVendorAuthRepository.buildSmallModelForm(synthesizeText);
        ThsSmallTtsRespDTO responseDTO = thsTtsSmallFeignClient.synthesize(
            formMap.get(TtsVendorConstant.FORM_KEY_PARAM),
            formMap.get(TtsVendorConstant.FORM_KEY_TS),
            formMap.get(TtsVendorConstant.FORM_KEY_SECRET_KEY));
        if (isSmallModelResponseInvalid(responseDTO)) {
            // 小模型失败时记录上下文，便于定位问题。
            log.error(
                "同花顺小模型TTS调用失败，traceId={}, modelCode={}, requestText={}, vendorCode={}, vendorMsg={}",
                traceId,
                modelCode,
                synthesizeText,
                resolveSmallModelVendorCode(responseDTO),
                resolveSmallModelVendorMessage(responseDTO));
            throw new BizException(ErrorCodeEnum.TTS_SMALL_MODEL_CALL_FAILED);
        }
        return TtsVendorResponseEntity.builder()
            .base64Audio(responseDTO.getData().getVoiceData())
            .vendorTraceId(responseDTO.getData().getLogId())
            .synthesizeText(synthesizeText)
            .build();
    }

    /**
     * 调用大模型端点。
     *
     * @param modelCode 模型编码
     * @param preparedTextEntity 预处理文本实体
     * @param traceId 链路追踪标识
     * @return 厂商响应
     */
    private TtsVendorResponseEntity invokeLargeModel(
        String modelCode,
        TtsPreparedTextEntity preparedTextEntity,
        String traceId) {
        // 大模型灾备场景统一改用 rawText。
        String synthesizeText = resolveLargeModelText(modelCode, preparedTextEntity, traceId);
        String voiceId = speechTtsProperties.getEndpoint().getLargeModelVoiceId();
        ThsLargeTtsReqDTO requestDTO = ThsLargeTtsReqDTO.builder()
            .voiceId(voiceId)
            .text(synthesizeText)
            .build();
        String authorization =
            ttsVendorAuthRepository.buildLargeModelAuthorization(JsonUtil.toJson(requestDTO));
        ThsLargeTtsRespDTO responseDTO = thsTtsLargeFeignClient.synthesize(authorization, requestDTO);
        if (isLargeModelResponseInvalid(responseDTO)) {
            // 大模型失败时记录上下文，便于定位问题。
            log.error(
                "同花顺大模型TTS调用失败，traceId={}, modelCode={}, voiceId={}, requestText={}, "
                    + "vendorCode={}, vendorErrorType={}, vendorMsg={}, vendorTraceId={}",
                traceId,
                modelCode,
                voiceId,
                synthesizeText,
                resolveLargeModelVendorCode(responseDTO),
                resolveLargeModelVendorErrorType(responseDTO),
                resolveLargeModelVendorMessage(responseDTO),
                resolveLargeModelVendorTraceId(responseDTO));
            throw new BizException(ErrorCodeEnum.TTS_LARGE_MODEL_CALL_FAILED);
        }
        return TtsVendorResponseEntity.builder()
            .base64Audio(responseDTO.getData().getAudio())
            .vendorTraceId(responseDTO.getTraceId())
            .synthesizeText(synthesizeText)
            .build();
    }

    /**
     * 解析大模型实际请求文本。
     *
     * @param modelCode 模型编码
     * @param preparedTextEntity 预处理文本实体
     * @param traceId 链路追踪标识
     * @return 大模型实际请求文本
     */
    private String resolveLargeModelText(
        String modelCode,
        TtsPreparedTextEntity preparedTextEntity,
        String traceId) {
        String rawText = preparedTextEntity.getRawText();
        String normalizedRawText = SsmlUtil.normalizeNameTag(
            rawText,
            speechTtsProperties.getNameTag().getNameTagStartTag(),
            speechTtsProperties.getNameTag().getCompatibleNameTagStartTagList());
        String requestText = normalizedRawText;
        boolean removedNameTag = false;
        // rawText 里如果已经带了 nameTag，需要先去掉再调用大模型，避免生成异常语音。
        if (SsmlUtil.containsNameTag(
            normalizedRawText,
            speechTtsProperties.getNameTag().getNameTagStartTag(),
            speechTtsProperties.getNameTag().getNameTagEndTag(),
            speechTtsProperties.getNameTag().getCompatibleNameTagStartTagList())) {
            requestText = SsmlUtil.removeNameTag(
                normalizedRawText,
                speechTtsProperties.getSsml().getSpeakStartTag(),
                speechTtsProperties.getSsml().getSpeakEndTag(),
                speechTtsProperties.getNameTag().getNameTagStartTag(),
                speechTtsProperties.getNameTag().getNameTagEndTag(),
                speechTtsProperties.getNameTag().getCompatibleNameTagStartTagList());
            removedNameTag = true;
        }
        if (!StringUtils.hasText(requestText)) {
            requestText = normalizedRawText;
        }
        // 记录大模型实际请求文本，便于灾备切换问题排查。
        log.info(
            "同花顺大模型TTS文本已切换为rawText，traceId={}, modelCode={}, removedNameTag={}, rawText={}, finalText={}, requestText={}",
            traceId,
            modelCode,
            removedNameTag,
            preparedTextEntity.getRawText(),
            preparedTextEntity.getFinalText(),
            requestText);
        return requestText;
    }

    /**
     * 判断小模型响应是否无效。
     *
     * @param responseDTO 小模型响应
     * @return 是否无效
     */
    private static boolean isSmallModelResponseInvalid(ThsSmallTtsRespDTO responseDTO) {
        if (responseDTO == null) {
            return true;
        }
        if (responseDTO.getCode() == null) {
            return true;
        }
        if (!TtsVendorConstant.SMALL_MODEL_SUCCESS_CODE.equals(responseDTO.getCode())) {
            return true;
        }
        return responseDTO.getData() == null;
    }

    /**
     * 判断大模型响应是否无效。
     *
     * @param responseDTO 大模型响应
     * @return 是否无效
     */
    private static boolean isLargeModelResponseInvalid(ThsLargeTtsRespDTO responseDTO) {
        if (responseDTO == null) {
            return true;
        }
        if (responseDTO.getCode() == null) {
            return true;
        }
        if (!TtsVendorConstant.LARGE_MODEL_SUCCESS_CODE.equals(responseDTO.getCode())) {
            return true;
        }
        return responseDTO.getData() == null;
    }

    /**
     * 提取小模型下游状态码。
     *
     * @param responseDTO 小模型响应
     * @return 下游状态码
     */
    private static String resolveSmallModelVendorCode(ThsSmallTtsRespDTO responseDTO) {
        if (responseDTO == null || responseDTO.getCode() == null) {
            return "null";
        }
        return String.valueOf(responseDTO.getCode());
    }

    /**
     * 提取小模型下游错误信息。
     *
     * @param responseDTO 小模型响应
     * @return 下游错误信息
     */
    private static String resolveSmallModelVendorMessage(ThsSmallTtsRespDTO responseDTO) {
        if (responseDTO == null || responseDTO.getNote() == null) {
            return "null";
        }
        return responseDTO.getNote();
    }

    /**
     * 提取大模型下游状态码。
     *
     * @param responseDTO 大模型响应
     * @return 下游状态码
     */
    private static String resolveLargeModelVendorCode(ThsLargeTtsRespDTO responseDTO) {
        if (responseDTO == null || responseDTO.getCode() == null) {
            return "null";
        }
        return String.valueOf(responseDTO.getCode());
    }

    /**
     * 提取大模型下游错误类型。
     *
     * @param responseDTO 大模型响应
     * @return 下游错误类型
     */
    private static String resolveLargeModelVendorErrorType(ThsLargeTtsRespDTO responseDTO) {
        if (responseDTO == null || responseDTO.getErrorType() == null) {
            return "null";
        }
        return responseDTO.getErrorType();
    }

    /**
     * 提取大模型下游错误信息。
     *
     * @param responseDTO 大模型响应
     * @return 下游错误信息
     */
    private static String resolveLargeModelVendorMessage(ThsLargeTtsRespDTO responseDTO) {
        if (responseDTO == null || responseDTO.getMsg() == null) {
            return "null";
        }
        return responseDTO.getMsg();
    }

    /**
     * 提取大模型下游 traceId。
     *
     * @param responseDTO 大模型响应
     * @return 下游 traceId
     */
    private static String resolveLargeModelVendorTraceId(ThsLargeTtsRespDTO responseDTO) {
        if (responseDTO == null || responseDTO.getTraceId() == null) {
            return "null";
        }
        return responseDTO.getTraceId();
    }

    /**
     * 单个端点调用器抽象。
     */
    @FunctionalInterface
    private interface TtsInvoker {

        /**
         * 使用预处理文本调用端点。
         *
         * @param modelCode 模型编码
         * @param preparedTextEntity 预处理文本实体
         * @param traceId 链路追踪标识
         * @return 厂商响应
         */
        TtsVendorResponseEntity invoke(String modelCode, TtsPreparedTextEntity preparedTextEntity, String traceId);
    }
}
