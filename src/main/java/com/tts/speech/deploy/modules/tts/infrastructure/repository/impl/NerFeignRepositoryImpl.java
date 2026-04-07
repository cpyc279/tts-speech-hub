package com.tts.speech.deploy.modules.tts.infrastructure.repository.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.tts.speech.deploy.common.constant.CommonConstant;
import com.tts.speech.deploy.common.enums.ErrorCodeEnum;
import com.tts.speech.deploy.common.exception.NerFeignException;
import com.tts.speech.deploy.common.metrics.TtsMetricsCollector;
import com.tts.speech.deploy.common.util.JsonUtil;
import com.tts.speech.deploy.modules.tts.domain.repository.NerFeignRepository;
import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechNerProperties;
import com.tts.speech.deploy.modules.tts.infrastructure.external.dto.NerLargeModelReqDTO;
import com.tts.speech.deploy.modules.tts.infrastructure.external.dto.NerLargeModelRespDTO;
import com.tts.speech.deploy.modules.tts.infrastructure.external.dto.NerReqDTO;
import com.tts.speech.deploy.modules.tts.infrastructure.external.feign.NerFeignClient;
import com.tts.speech.deploy.modules.tts.infrastructure.external.feign.NerLargeModelFeignClient;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * NER 仓储实现。
 *
 * @author yangchen5
 * @since 2026-03-18 10:30:00
 */
@Slf4j
@Service
public class NerFeignRepositoryImpl implements NerFeignRepository {

    /**
     * 首条结果索引。
     */
    private static final int FIRST_RESULT_INDEX = 0;

    /**
     * 新版 NER 大模型成功状态码。
     */
    private static final int LARGE_MODEL_SUCCESS_STATUS_CODE = 200;

    /**
     * 新版 NER 固定 JSON 中的人名字段。
     */
    private static final String LARGE_MODEL_NAME_FIELD = "name";

    /**
     * 旧版 NER 人名 schema 字段值。
     */
    private static final String LEGACY_NER_PERSON_NAME_SCHEMA = "姓名";

    @Resource
    private NerFeignClient nerFeignClient;

    @Resource
    private NerLargeModelFeignClient nerLargeModelFeignClient;

    @Resource
    private SpeechNerProperties speechNerProperties;

    @Resource
    private TtsMetricsCollector ttsMetricsCollector;

    @Resource
    private NerTemplateCacheSupport nerTemplateCacheSupport;

    /**
     * 按券商维度匹配可直接使用的成熟模板。
     *
     * @param brokerId 券商编号
     * @param rawText 原始文本
     * @param traceId 链路追踪标识
     * @return 模板命中的最终文本
     */
    @Override
    public Optional<String> matchAvailableTemplate(String brokerId, String rawText, String traceId) {
        // 模板缓存匹配与淘汰逻辑统一下沉到模板支持组件。
        return nerTemplateCacheSupport.matchAvailableTemplate(brokerId, rawText, traceId);
    }


    /**
     * ?? NER ?????????
     */
    @Override
    public List<String> parsePeopleNames(String brokerId, String userId, String rawText, String traceId) {
        String sessionId = resolveSessionId(traceId);
        try {
            List<String> peopleNameList = parsePeopleNamesByLargeModel(userId, rawText, traceId, sessionId);
            ttsMetricsCollector.recordNerLargeModelSuccess(brokerId);
            log.info("??NER???????brokerId={}, userId={}, traceId={}, peopleNameList={}",
                brokerId, userId, traceId, peopleNameList);
            return peopleNameList;
        } catch (NerFeignException largeModelException) {
            ttsMetricsCollector.recordNerLargeModelFailed(brokerId);
            log.warn("??NER???????????NER?brokerId={}, userId={}, traceId={}, rawText={}",
                brokerId, userId, traceId, rawText, largeModelException);
            return parsePeopleNamesWithLegacyFallback(brokerId, userId, rawText, traceId, largeModelException);
        } catch (RuntimeException exception) {
            ttsMetricsCollector.recordNerLargeModelFailed(brokerId);
            log.warn("??NER???????????NER?brokerId={}, userId={}, traceId={}, rawText={}",
                brokerId, userId, traceId, rawText, exception);
            return parsePeopleNamesWithLegacyFallback(
                brokerId,
                userId,
                rawText,
                traceId,
                new NerFeignException(ErrorCodeEnum.NER_CALL_FAILED, exception));
        }
    }

    /**
     * ??????????????????
     */
    @Override
    public void mergeTemplate(String brokerId, String rawText, List<String> peopleNameList, String traceId) {
        nerTemplateCacheSupport.mergeTemplate(brokerId, rawText, peopleNameList, traceId);
    }

    private List<String> parsePeopleNamesWithLegacyFallback(
        String brokerId,
        String userId,
        String rawText,
        String traceId,
        NerFeignException largeModelException) {
        try {
            List<String> peopleNameList = parsePeopleNamesByLegacyModel(brokerId, rawText, traceId);
            ttsMetricsCollector.recordNerLargeModelFallback(brokerId);
            log.info("??NER????NER???brokerId={}, userId={}, traceId={}, peopleNameList={}",
                brokerId, userId, traceId, peopleNameList);
            return peopleNameList;
        } catch (NerFeignException legacyException) {
            ttsMetricsCollector.recordNerLargeModelAllFailed(brokerId);
            log.error("??NER???NER????brokerId={}, userId={}, traceId={}, rawText={}, largeModelMessage={}",
                brokerId, userId, traceId, rawText, largeModelException.getMessage(), legacyException);
            throw legacyException;
        } catch (RuntimeException exception) {
            ttsMetricsCollector.recordNerLargeModelAllFailed(brokerId);
            log.error("??NER???NER????brokerId={}, userId={}, traceId={}, rawText={}, largeModelMessage={}",
                brokerId, userId, traceId, rawText, largeModelException.getMessage(), exception);
            throw new NerFeignException(ErrorCodeEnum.NER_CALL_FAILED, exception);
        }
    }

    private List<String> parsePeopleNamesByLargeModel(
        String userId,
        String rawText,
        String traceId,
        String sessionId) {
        try {
            NerLargeModelRespDTO responseDTO = nerLargeModelFeignClient.chat(
                resolveLargeModelRemoteIp(),
                resolveLargeModelRemoteSvc(),
                sessionId,
                traceId,
                normalizeUserId(userId),
                buildLargeModelRequest(rawText));
            validateLargeModelResponse(responseDTO);
            return extractPeopleNamesFromLargeModelText(responseDTO.getResponse().getText());
        } catch (NerFeignException nerFeignException) {
            throw nerFeignException;
        } catch (RuntimeException exception) {
            throw new NerFeignException(ErrorCodeEnum.NER_CALL_FAILED, exception);
        }
    }

    private List<String> parsePeopleNamesByLegacyModel(String brokerId, String rawText, String traceId) {
        try {
            Map<String, Object> responseMap = nerFeignClient.extract(
                NerReqDTO.builder()
                    .text(rawText)
                    .schema(NerReqDTO.Schema.builder().personName(LEGACY_NER_PERSON_NAME_SCHEMA).build())
                    .build());
            List<String> peopleNameList = extractPeopleNames(responseMap);
            log.info("??NER???????brokerId={}, traceId={}, peopleNameList={}", brokerId, traceId, peopleNameList);
            return peopleNameList;
        } catch (NerFeignException nerFeignException) {
            throw nerFeignException;
        } catch (RuntimeException exception) {
            throw new NerFeignException(ErrorCodeEnum.NER_CALL_FAILED, exception);
        }
    }

    private NerLargeModelReqDTO buildLargeModelRequest(String rawText) {
        SpeechNerProperties.LargeModelConfig largeModelConfig = getLargeModelConfig();
        // 新版请求体固定包含 mode、pipeName 和 query 三个核心字段。
        return NerLargeModelReqDTO.builder()
            .mode(largeModelConfig.getMode())
            .pipeName(largeModelConfig.getPipeName())
            .inputVariableValue(NerLargeModelReqDTO.InputVariableValue.builder().query(rawText).build())
            .build();
    }

    /**
     * 校验新版 NER 外层响应。
     *
     * @param responseDTO 响应 DTO
     */
    private void validateLargeModelResponse(NerLargeModelRespDTO responseDTO) {
        if (responseDTO == null) {
            throw buildLargeModelFailureException("新版NER响应为空");
        }
        Integer statusCode = responseDTO.getStatusCode();
        if (statusCode == null || statusCode != LARGE_MODEL_SUCCESS_STATUS_CODE) {
            throw buildLargeModelFailureException("新版NER返回状态码异常");
        }
        NerLargeModelRespDTO.Response response = responseDTO.getResponse();
        if (response == null) {
            throw buildLargeModelFailureException("新版NER响应体为空");
        }
        if (!StringUtils.hasText(response.getText())) {
            throw buildLargeModelFailureException("新版NER响应文本为空");
        }
    }

    /**
     * 解析新版 NER 固定 JSON 字符串。
     *
     * @param responseText 固定 JSON 字符串
     * @return 人名列表
     */
    private List<String> extractPeopleNamesFromLargeModelText(String responseText) {
        try {
            // 先把固定 JSON 字符串解析为 JsonNode，统一处理字段类型差异。
            JsonNode rootNode = JsonUtil.readTree(responseText);
            JsonNode nameNode = rootNode.get(LARGE_MODEL_NAME_FIELD);
            if (nameNode == null) {
                throw buildLargeModelFailureException("新版NER固定JSON缺少name字段");
            }
            // name 字段存在但为空时，表示新版调用成功且未识别到人名。
            return extractPeopleNamesFromLargeModelNode(nameNode);
        } catch (NerFeignException nerFeignException) {
            throw nerFeignException;
        } catch (RuntimeException exception) {
            throw new NerFeignException(ErrorCodeEnum.NER_CALL_FAILED, "新版NER固定JSON解析失败", exception);
        }
    }

    /**
     * 从新版 NER 的 name 节点提取人名列表。
     *
     * @param nameNode name 节点
     * @return 人名列表
     */
    private static List<String> extractPeopleNamesFromLargeModelNode(JsonNode nameNode) {
        if (nameNode.isNull()) {
            return Collections.emptyList();
        }
        if (nameNode.isArray()) {
            return extractPeopleNamesFromArrayNode(nameNode);
        }
        if (nameNode.isTextual()) {
            String peopleName = nameNode.asText();
            if (!StringUtils.hasText(peopleName)) {
                return Collections.emptyList();
            }
            return List.of(peopleName);
        }
        throw buildLargeModelFailureException("新版NER返回name字段类型非法");
    }

    /**
     * 从新版 NER 的数组节点提取人名列表。
     *
     * @param arrayNode 数组节点
     * @return 人名列表
     */
    private static List<String> extractPeopleNamesFromArrayNode(JsonNode arrayNode) {
        List<String> peopleNameList = new ArrayList<>();
        for (JsonNode peopleNode : arrayNode) {
            // 先跳过空节点，避免把 null 写入结果。
            if (peopleNode == null || peopleNode.isNull()) {
                continue;
            }
            String peopleName = peopleNode.asText();
            // 只保留有内容的人名文本。
            if (StringUtils.hasText(peopleName)) {
                peopleNameList.add(peopleName);
            }
        }
        if (CollectionUtils.isEmpty(peopleNameList)) {
            return Collections.emptyList();
        }
        return peopleNameList;
    }

    /**
     * 获取新版 NER 配置。
     *
     * @return 新版配置
     */
    private SpeechNerProperties.LargeModelConfig getLargeModelConfig() {
        SpeechNerProperties.LargeModelConfig largeModelConfig = speechNerProperties.getLargeModel();
        if (largeModelConfig == null) {
            throw buildLargeModelFailureException("新版NER配置缺失");
        }
        return largeModelConfig;
    }

    /**
     * 解析新版 NER 调用方 IP。
     *
     * @return 调用方 IP
     */
    private String resolveLargeModelRemoteIp() {
        SpeechNerProperties.LargeModelConfig largeModelConfig = getLargeModelConfig();
        if (StringUtils.hasText(largeModelConfig.getRemoteIp())) {
            return largeModelConfig.getRemoteIp();
        }
        return CommonConstant.UNKNOWN;
    }

    /**
     * 解析新版 NER 调用方服务名。
     *
     * @return 调用方服务名
     */
    private String resolveLargeModelRemoteSvc() {
        SpeechNerProperties.LargeModelConfig largeModelConfig = getLargeModelConfig();
        if (StringUtils.hasText(largeModelConfig.getRemoteSvc())) {
            return largeModelConfig.getRemoteSvc();
        }
        return CommonConstant.UNKNOWN;
    }

    /**
     * 归一化用户编号。
     *
     * @param userId 用户编号
     * @return 归一化后的用户编号
     */
    private static String normalizeUserId(String userId) {
        if (StringUtils.hasText(userId)) {
            return userId;
        }
        return CommonConstant.UNKNOWN;
    }

    /**
     * 解析会话标识。
     *
     * @param traceId 链路追踪标识
     * @return 会话标识
     */
    private static String resolveSessionId(String traceId) {
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }
        return CommonConstant.UNKNOWN;
    }

    /**
     * 构建新版 NER 失败异常。
     *
     * @param message 异常消息
     * @return NER 异常
     */
    private static NerFeignException buildLargeModelFailureException(String message) {
        return new NerFeignException(ErrorCodeEnum.NER_CALL_FAILED, message, null);
    }

    /**
     * 从 NER 响应中提取人名列表。
     *
     * @param responseMap 响应体
     * @return 人名列表
     */
    @SuppressWarnings("unchecked")
    private static List<String> extractPeopleNames(Map<String, Object> responseMap) {
        // results 为空或结构不符时，按“未识别到人名”处理。
        Object resultsObject = responseMap.get(CommonConstant.RESULTS_KEY);
        if (!(resultsObject instanceof List<?>) || CollectionUtils.isEmpty((List<?>) resultsObject)) {
            return Collections.emptyList();
        }
        List<?> results = (List<?>) resultsObject;
        // 当前只消费第一条识别结果，保持与现有协议一致。
        Object firstResult = results.get(FIRST_RESULT_INDEX);
        if (!(firstResult instanceof Map<?, ?>)) {
            return Collections.emptyList();
        }
        Map<?, ?> resultMap = (Map<?, ?>) firstResult;
        Object peopleObject = resultMap.get(CommonConstant.PEOPLE_NAME_KEY);
        if (!(peopleObject instanceof List<?>)) {
            return Collections.emptyList();
        }
        List<?> peopleList = (List<?>) peopleObject;
        // 过滤掉空串和非字符串脏数据，只保留有效姓名文本。
        return peopleList.stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .map(peopleMap -> peopleMap.get(CommonConstant.TEXT_KEY))
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .filter(NerFeignRepositoryImpl::hasNonWhitespaceText)
            .toList();
    }

    /**
     * 判断文本是否包含非空白字符。
     *
     * @param text 待判断文本
     * @return 是否包含非空白字符
     */
    private static boolean hasNonWhitespaceText(String text) {
        // 通过 trim 后判空，过滤掉空串和纯空白字符。
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return StringUtils.hasText(text.trim());
    }
}
