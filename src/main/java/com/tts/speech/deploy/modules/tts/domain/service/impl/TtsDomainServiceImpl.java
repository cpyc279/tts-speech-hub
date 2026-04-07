package com.tts.speech.deploy.modules.tts.domain.service.impl;

import com.tts.speech.deploy.common.constant.CommonConstant;
import com.tts.speech.deploy.common.constant.StringPool;
import com.tts.speech.deploy.common.enums.ErrorCodeEnum;
import com.tts.speech.deploy.common.enums.TtsEndpointEnum;
import com.tts.speech.deploy.common.exception.BizAssert;
import com.tts.speech.deploy.common.exception.BizException;
import com.tts.speech.deploy.common.exception.NerFeignException;
import com.tts.speech.deploy.common.metrics.TtsMetricsCollector;
import com.tts.speech.deploy.common.util.Md5Util;
import com.tts.speech.deploy.common.util.SsmlUtil;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsAudioCacheEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsAudioEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsPreparedTextEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsRequestEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsResponseEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsRouteEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsVendorResponseEntity;
import com.tts.speech.deploy.modules.tts.domain.repository.BrokerVoiceRouteRepository;
import com.tts.speech.deploy.modules.tts.domain.repository.NerFeignRepository;
import com.tts.speech.deploy.modules.tts.domain.repository.OssStorageRepository;
import com.tts.speech.deploy.modules.tts.domain.repository.TtsCacheRepository;
import com.tts.speech.deploy.modules.tts.domain.repository.TtsVendorFeignRepository;
import com.tts.speech.deploy.modules.tts.domain.service.TtsDomainService;
import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechTtsProperties;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * TTS 领域服务实现。
 *
 * @author yangchen5
 * @since 2026-03-18 10:30:00
 */
@Slf4j
@Service
public class TtsDomainServiceImpl implements TtsDomainService {

    /**
     * 初始顺序号。
     */
    private static final int INITIAL_SEQUENCE = 0;

    /**
     * 批量文本超限提示。
     */
    private static final String RAW_TEXT_LIST_EXCEEDS_BATCH_SIZE_MESSAGE = "raw_text_list exceeds max batch size";

    /**
     * 单条文本超限提示。
     */
    private static final String RAW_TEXT_EXCEEDS_MAX_TEXT_LENGTH_MESSAGE = "raw_text exceeds max text length";

    /**
     * 厂商返回空音频提示。
     */
    private static final String VENDOR_RESPONSE_BASE64_EMPTY_MESSAGE = "vendor tts response base64 is empty";

    /**
     * 券商路由仓储。
     */
    @Resource
    private BrokerVoiceRouteRepository brokerVoiceRouteRepository;

    /**
     * TTS 缓存仓储。
     */
    @Resource
    private TtsCacheRepository ttsCacheRepository;

    /**
     * NER 仓储。
     */
    @Resource
    private NerFeignRepository nerFeignRepository;

    /**
     * OSS 存储仓储。
     */
    @Resource
    private OssStorageRepository ossStorageRepository;

    /**
     * 厂商 TTS 仓储。
     */
    @Resource
    private TtsVendorFeignRepository ttsVendorFeignRepository;

    /**
     * TTS 配置。
     */
    @Resource
    private SpeechTtsProperties speechTtsProperties;

    /**
     * 预处理线程池。
     */
    @Resource(name = "ttsPrepareExecutor")
    private Executor ttsPrepareExecutor;

    /**
     * 合成线程池。
     */
    @Resource(name = "ttsSynthesizeExecutor")
    private Executor ttsSynthesizeExecutor;

    /**
     * OSS 上传线程池。
     */
    @Resource(name = "ttsOssUploadExecutor")
    private Executor ttsOssUploadExecutor;

    /**
     * TTS 指标采集器。
     */
    @Resource
    private TtsMetricsCollector ttsMetricsCollector;

    /**
     * 执行端到端 TTS 合成。
     *
     * @param requestEntity TTS 请求
     * @return TTS 响应
     */
    @Override
    public TtsResponseEntity synthesize(TtsRequestEntity requestEntity) {
        // 先校验请求参数，避免非法数据进入主链路。
        validateRequest(requestEntity);

        // 根据券商路由解析本次请求应该走的模型和主备端点。
        TtsRouteEntity routeEntity = brokerVoiceRouteRepository.resolveRoute(requestEntity.getBrokerId());

        // 文本预处理阶段优先完成 name 标签兼容和缓存短路。
        List<TtsPreparedTextEntity> preparedTextEntityList = prepareTexts(requestEntity, routeEntity.getModelCode());
        if (isAllPreparedTextCacheHit(preparedTextEntityList)) {
            // 全部命中缓存时直接返回缓存音频，同时异步补齐缺失的 URL 缓存。
            List<TtsAudioEntity> cacheHitAudioEntityList = buildCacheHitAudioList(preparedTextEntityList);
            asyncUploadAudioAndCacheUrl(
                cacheHitAudioEntityList,
                requestEntity.getTraceId(),
                requestEntity.getUserId(),
                requestEntity.getBrokerId(),
                requestEntity.getBusinessCode());
            return TtsResponseEntity.builder().audioList(cacheHitAudioEntityList).build();
        }

        // 只对未命中缓存的文本走厂商合成链路。
        List<TtsAudioEntity> audioEntityList = executeWithFallback(
            preparedTextEntityList,
            routeEntity,
            requestEntity.getTraceId());

        // 新音频写回 Base64 缓存，供后续请求复用。
        persistCache(audioEntityList, routeEntity.getModelCode(), requestEntity.getBrokerId());

        // 音频 URL 上传与回填是后置动作，异步触发即可。
        asyncUploadAudioAndCacheUrl(
            audioEntityList,
            requestEntity.getTraceId(),
            requestEntity.getUserId(),
            requestEntity.getBrokerId(),
            requestEntity.getBusinessCode());
        return TtsResponseEntity.builder().audioList(audioEntityList).build();
    }

    /**
     * 校验领域请求规则。
     *
     * @param requestEntity TTS 请求
     */
    @Override
    public void validateRequest(TtsRequestEntity requestEntity) {
        // 先校验批量条数，避免单次请求携带过多文本压垮下游。
        BizAssert.isTrue(
            requestEntity.getRawTextList().size() <= speechTtsProperties.getMaxBatchSize(),
            ErrorCodeEnum.INVALID_PARAMETER,
            RAW_TEXT_LIST_EXCEEDS_BATCH_SIZE_MESSAGE);

        // 再逐条校验文本长度，保证每条文本都在允许范围内。
        for (String rawText : requestEntity.getRawTextList()) {
            validateRawTextLength(rawText, requestEntity.getBrokerId());
        }
    }

    /**
     * 为批量文本准备预处理结果。
     *
     * @param requestEntity TTS 请求
     * @param modelCode 模型编码
     * @return 预处理文本列表
     */
    @Override
    public List<TtsPreparedTextEntity> prepareTexts(TtsRequestEntity requestEntity, String modelCode) {
        List<String> rawTextList = requestEntity.getRawTextList();
        List<TtsPreparedTextEntity> preparedTextEntityList = new ArrayList<>(rawTextList.size());
        List<Integer> uncachedTextIndexList = new ArrayList<>();

        // 第一阶段先同步判断是否可以直接短路。
        for (int index = INITIAL_SEQUENCE; index < rawTextList.size(); index++) {
            String rawText = rawTextList.get(index);
            TtsPreparedTextEntity preparedTextEntity =
                tryBuildPreparedTextFromCache(rawText, index, requestEntity, modelCode);
            if (preparedTextEntity != null) {
                preparedTextEntityList.add(preparedTextEntity);
                continue;
            }
            uncachedTextIndexList.add(index);
        }

        // 第二阶段只处理未命中缓存的文本。
        if (!uncachedTextIndexList.isEmpty()) {
            preparedTextEntityList.addAll(
                prepareUncachedTexts(rawTextList, uncachedTextIndexList, requestEntity, modelCode));
        }
        List<TtsPreparedTextEntity> sortedPreparedTextEntityList = new ArrayList<>(preparedTextEntityList);
        sortedPreparedTextEntityList.sort(Comparator.comparing(TtsPreparedTextEntity::getSequence));
        return sortedPreparedTextEntityList;
    }

    /**
     * 执行主路端点，并在需要时切换备用端点。
     *
     * @param preparedTextEntityList 预处理文本列表
     * @param routeEntity 路由结果
     * @param traceId 链路追踪标识
     * @return 音频列表
     */
    @Override
    public List<TtsAudioEntity> executeWithFallback(
        List<TtsPreparedTextEntity> preparedTextEntityList,
        TtsRouteEntity routeEntity,
        String traceId) {
        try {
            // 主通道成功时直接返回，不进入备用通道。
            return executeSingleRound(
                preparedTextEntityList,
                routeEntity.getPrimaryEndpoint(),
                routeEntity.getModelCode(),
                traceId);
        } catch (RuntimeException primaryException) {
            ttsMetricsCollector.recordPrimaryToFallback(
                routeEntity.getPrimaryEndpoint().getCode(),
                routeEntity.getFallbackEndpoint().getCode());
            log.error(
                "TTS主通道合成失败，准备切换备用通道，traceId={}, primaryEndpoint={}, fallbackEndpoint={}",
                traceId,
                routeEntity.getPrimaryEndpoint(),
                routeEntity.getFallbackEndpoint(),
                primaryException);
            try {
                // 进入备用通道时只重试厂商合成，不重复做文本预处理。
                return executeSingleRound(
                    preparedTextEntityList,
                    routeEntity.getFallbackEndpoint(),
                    routeEntity.getModelCode(),
                    traceId);
            } catch (RuntimeException fallbackException) {
                ttsMetricsCollector.recordAllFailed(
                    routeEntity.getPrimaryEndpoint().getCode(),
                    routeEntity.getFallbackEndpoint().getCode());
                log.error(
                    "TTS主备通道均合成失败，traceId={}, primaryEndpoint={}, fallbackEndpoint={}",
                    traceId,
                    routeEntity.getPrimaryEndpoint(),
                    routeEntity.getFallbackEndpoint(),
                    fallbackException);
                throw new BizException(ErrorCodeEnum.TTS_ALL_FAILED, primaryException.getMessage());
            }
        }
    }

    /**
     * 执行一轮整批端点合成。
     *
     * @param preparedTextEntityList 预处理文本列表
     * @param endpointEnum 目标端点
     * @param modelCode 模型编码
     * @param traceId 链路追踪标识
     * @return 音频列表
     */
    @Override
    public List<TtsAudioEntity> executeSingleRound(
        List<TtsPreparedTextEntity> preparedTextEntityList,
        TtsEndpointEnum endpointEnum,
        String modelCode,
        String traceId) {
        // 每条文本构建一个独立合成任务，缓存命中时会直接返回已完成 Future。
        List<CompletableFuture<TtsAudioEntity>> futureList = preparedTextEntityList.stream()
            .map(preparedTextEntity -> buildSynthesizeFuture(preparedTextEntity, endpointEnum, modelCode, traceId))
            .toList();
        waitAllFutures(futureList);
        return futureList.stream()
            .map(CompletableFuture::join)
            .sorted(Comparator.comparing(TtsAudioEntity::getSequence))
            .toList();
    }

    /**
     * 上传全部音频到 OSS。
     *
     * @param audioEntityList 音频列表
     * @param traceId 链路追踪标识
     * @return 上传后的音频列表
     */
    @Override
    public List<TtsAudioEntity> uploadAudioList(List<TtsAudioEntity> audioEntityList, String traceId) {
        // 上传属于 IO 密集任务，统一交给独立线程池处理。
        List<CompletableFuture<TtsAudioEntity>> futureList = audioEntityList.stream()
            .map(audioEntity -> CompletableFuture.supplyAsync(
                () -> uploadSingleAudio(audioEntity, traceId),
                ttsOssUploadExecutor))
            .toList();
        waitAllFutures(futureList);
        return futureList.stream()
            .map(CompletableFuture::join)
            .sorted(Comparator.comparing(TtsAudioEntity::getSequence))
            .toList();
    }

    /**
     * 持久化非 name 标签文本的缓存记录。
     *
     * @param audioEntityList 音频列表
     * @param modelCode 模型编码
     * @param brokerId 券商 ID
     */
    @Override
    public void persistCache(List<TtsAudioEntity> audioEntityList, String modelCode, String brokerId) {
        // 含 name 标签的文本不进入公共缓存，避免污染不同用户的读法。
        for (TtsAudioEntity audioEntity : audioEntityList) {
            if (shouldPersistAudioCache(audioEntity)) {
                writeCache(audioEntity, modelCode, brokerId);
            }
        }
    }

    /**
     * 判断当前音频是否允许写入公共缓存。
     *
     * @param audioEntity 音频实体
     * @return 是否允许写入缓存
     */
    private boolean shouldPersistAudioCache(TtsAudioEntity audioEntity) {
        // 含 name 标签的文本和已命中缓存的音频都不再重复写入公共缓存。
        boolean hasNameTag = containsNameTag(audioEntity.getFinalText());
        boolean audioCacheHit = Boolean.TRUE.equals(audioEntity.getCacheHit());
        return !hasNameTag && !audioCacheHit;
    }

    /**
     * 按未命中文本数量选择预处理执行方式。
     *
     * @param rawTextList 原始文本列表
     * @param uncachedTextIndexList 未命中文本下标列表
     * @param requestEntity 请求对象
     * @param modelCode 模型编码
     * @return 预处理文本列表
     */
    private List<TtsPreparedTextEntity> prepareUncachedTexts(
        List<String> rawTextList,
        List<Integer> uncachedTextIndexList,
        TtsRequestEntity requestEntity,
        String modelCode) {
        if (uncachedTextIndexList.size() == 1) {
            // 只有一条文本时直接同步处理，避免无意义线程切换。
            Integer index = uncachedTextIndexList.get(INITIAL_SEQUENCE);
            return List.of(prepareUncachedText(rawTextList.get(index), index, requestEntity, modelCode));
        }

        // 多条文本时才进入预处理线程池并发执行。
        List<CompletableFuture<TtsPreparedTextEntity>> futureList = new ArrayList<>(uncachedTextIndexList.size());
        for (Integer index : uncachedTextIndexList) {
            String rawText = rawTextList.get(index);
            futureList.add(CompletableFuture.supplyAsync(
                () -> prepareUncachedText(rawText, index, requestEntity, modelCode),
                ttsPrepareExecutor));
        }
        waitAllFutures(futureList);

        // 汇总异步结果，后续统一排序。
        List<TtsPreparedTextEntity> preparedTextEntityList = new ArrayList<>(futureList.size());
        for (CompletableFuture<TtsPreparedTextEntity> future : futureList) {
            preparedTextEntityList.add(future.join());
        }
        return preparedTextEntityList;
    }

    /**
     * 尝试构建可直接短路的预处理文本。
     *
     * @param rawText 原始文本
     * @param sequence 顺序号
     * @param requestEntity 请求对象
     * @param modelCode 模型编码
     * @return 预处理文本，未命中时返回 null
     */
    private TtsPreparedTextEntity tryBuildPreparedTextFromCache(
        String rawText,
        int sequence,
        TtsRequestEntity requestEntity,
        String modelCode) {
        // 先把兼容 name 标签标准化，再判断是否已经带标签。
        String normalizedRawText = normalizeNameTag(rawText);
        if (containsNameTag(normalizedRawText)) {
            return buildPreparedTextEntity(
                sequence,
                normalizedRawText,
                normalizedRawText,
                true,
                null,
                false,
                true);
        }

        // 再按纯文本构建缓存键，优先复用既有缓存。
        String plainTextCacheKey = buildCacheKey(requestEntity.getBrokerId(), modelCode, rawText);
        Optional<TtsAudioCacheEntity> cachedAudioCacheOptional =
            findPlainTextAudioCache(plainTextCacheKey, requestEntity, rawText);
        if (cachedAudioCacheOptional.isEmpty()) {
            return null;
        }

        // 缓存命中时补齐 Base64 和 URL，并直接结束后续预处理。
        TtsAudioCacheEntity audioCacheEntity = cachedAudioCacheOptional.get();
        return buildPreparedTextEntity(sequence, rawText, rawText, false, plainTextCacheKey, true, false)
            .toBuilder()
            .cachedBase64Audio(audioCacheEntity.getBase64Audio())
            .cachedAudioUrl(findAudioUrlCache(requestEntity.getBrokerId(), plainTextCacheKey))
            .build();
    }

    /**
     * 预处理单条未命中缓存的文本。
     *
     * @param rawText 原始文本
     * @param sequence 顺序号
     * @param requestEntity 请求对象
     * @param modelCode 模型编码
     * @return 预处理文本实体
     */
    private TtsPreparedTextEntity prepareUncachedText(
        String rawText,
        int sequence,
        TtsRequestEntity requestEntity,
        String modelCode) {
        // 先标准化原始文本，再进入模板匹配和 NER 处理。
        String normalizedRawText = normalizeNameTag(rawText);
        String finalText = normalizeNameTag(resolveFinalText(normalizedRawText, requestEntity));
        boolean finalTextHasNameTag = containsNameTag(finalText);
        String cacheKey = buildCacheKey(requestEntity.getBrokerId(), modelCode, finalText);
        return buildPreparedTextEntity(
            sequence,
            normalizedRawText,
            finalText,
            finalTextHasNameTag,
            cacheKey,
            false,
            true);
    }

    /**
     * 查询纯文本音频缓存。
     *
     * @param plainTextCacheKey 纯文本缓存键
     * @param requestEntity 请求对象
     * @param rawText 原始文本
     * @return 音频缓存
     */
    private Optional<TtsAudioCacheEntity> findPlainTextAudioCache(
        String plainTextCacheKey,
        TtsRequestEntity requestEntity,
        String rawText) {
        TtsAudioCacheEntity audioCacheEntity =
            ttsCacheRepository.getAudioCache(requestEntity.getBrokerId(), plainTextCacheKey);
        if (audioCacheEntity == null || !StringUtils.hasText(audioCacheEntity.getBase64Audio())) {
            ttsMetricsCollector.recordAudioCacheMiss(requestEntity.getBrokerId());
            return Optional.empty();
        }

        // 命中缓存时记录指标和日志，便于观察缓存收益。
        ttsMetricsCollector.recordAudioCacheHit(requestEntity.getBrokerId());
        log.info(
            "TTS plain-text音频缓存命中，brokerId={}, traceId={}, rawText={}",
            requestEntity.getBrokerId(),
            requestEntity.getTraceId(),
            rawText);
        return Optional.of(audioCacheEntity);
    }

    /**
     * 查询音频 URL 缓存。
     *
     * @param brokerId 券商 ID
     * @param base64CacheKey Base64 缓存键
     * @return 音频 URL
     */
    private String findAudioUrlCache(String brokerId, String base64CacheKey) {
        if (!StringUtils.hasText(base64CacheKey)) {
            return null;
        }
        return ttsCacheRepository.getAudioUrlCache(brokerId, buildUrlCacheKey(base64CacheKey));
    }

    /**
     * 解析最终文本。
     *
     * @param rawText 原始文本
     * @param requestEntity 请求对象
     * @return 最终文本
     */
    private String resolveFinalText(String rawText, TtsRequestEntity requestEntity) {
        // 优先匹配成熟模板，命中时直接返回模板结果。
        Optional<String> matchedTemplateOptional = nerFeignRepository.matchAvailableTemplate(
            requestEntity.getBrokerId(),
            rawText,
            requestEntity.getTraceId());
        if (matchedTemplateOptional.isPresent()) {
            return matchedTemplateOptional.get();
        }

        // 模板未命中时，进入 NER 识别与降级处理。
        return processTextWithFallback(rawText, requestEntity);
    }

    /**
     * 调用 NER 服务并在失败时降级为原文。
     *
     * @param rawText 原始文本
     * @param requestEntity 请求对象
     * @return 最终文本
     */
    private String processTextWithFallback(String rawText, TtsRequestEntity requestEntity) {
        try {
            // 先通过 NER 抽取人名，再决定是否补充 name 标签。
            List<String> peopleNameList = nerFeignRepository.parsePeopleNames(
                requestEntity.getBrokerId(),
                requestEntity.getUserId(),
                rawText,
                requestEntity.getTraceId());
            if (peopleNameList.isEmpty()) {
                log.info(
                    "NER未识别到人名，brokerId={}, traceId={}, rawText={}",
                    requestEntity.getBrokerId(),
                    requestEntity.getTraceId(),
                    rawText);
                return rawText;
            }

            // 识别到人名后先回写模板统计，为后续模板命中做准备。
            nerFeignRepository.mergeTemplate(
                requestEntity.getBrokerId(),
                rawText,
                peopleNameList,
                requestEntity.getTraceId());
            return wrapTextWithNameTags(rawText, peopleNameList);
        } catch (NerFeignException nerFeignException) {
            ttsMetricsCollector.recordNerDegradeToRawText(requestEntity.getBrokerId());
            log.warn(
                "NER调用失败，降级使用原文，brokerId={}, traceId={}, rawText={}",
                requestEntity.getBrokerId(),
                requestEntity.getTraceId(),
                rawText,
                nerFeignException);
            return rawText;
        }
    }

    /**
     * 给文本中的人名补充 name 标签。
     *
     * @param rawText 原始文本
     * @param peopleNameList 人名列表
     * @return 最终文本
     */
    private String wrapTextWithNameTags(String rawText, List<String> peopleNameList) {
        String nameTagStart = speechTtsProperties.getNameTag().getNameTagStartTag();
        String nameTagEnd = speechTtsProperties.getNameTag().getNameTagEndTag();
        String wrappedText = rawText;
        for (String peopleName : peopleNameList) {
            // 对每个识别到的人名执行全量替换，保证读法一致。
            wrappedText = wrappedText.replace(peopleName, nameTagStart + peopleName + nameTagEnd);
        }
        return appendSpeakTagIfNecessary(wrappedText);
    }

    /**
     * 按需补齐 speak 标签。
     *
     * @param wrappedText 包装后的文本
     * @return 最终文本
     */
    private String appendSpeakTagIfNecessary(String wrappedText) {
        String speakStartTag = speechTtsProperties.getSsml().getSpeakStartTag();
        String speakEndTag = speechTtsProperties.getSsml().getSpeakEndTag();
        if (wrappedText.contains(speakStartTag) && wrappedText.contains(speakEndTag)) {
            return wrappedText;
        }
        return speakStartTag + wrappedText + speakEndTag;
    }

    /**
     * 构建单条合成异步任务。
     *
     * @param preparedTextEntity 预处理文本
     * @param endpointEnum 端点枚举
     * @param modelCode 模型编码
     * @param traceId 链路追踪标识
     * @return 异步任务
     */
    private CompletableFuture<TtsAudioEntity> buildSynthesizeFuture(
        TtsPreparedTextEntity preparedTextEntity,
        TtsEndpointEnum endpointEnum,
        String modelCode,
        String traceId) {
        if (Boolean.TRUE.equals(preparedTextEntity.getAudioCacheHit())) {
            // 预处理阶段已命中缓存时，直接构造已完成结果。
            return CompletableFuture.completedFuture(
                buildAudioEntity(
                    preparedTextEntity,
                    preparedTextEntity.getCachedBase64Audio(),
                    null,
                    preparedTextEntity.getFinalText()));
        }

        // 未命中缓存的文本才进入厂商合成线程池。
        return CompletableFuture.supplyAsync(
            () -> synthesizeSingleText(preparedTextEntity, endpointEnum, modelCode, traceId),
            ttsSynthesizeExecutor);
    }

    /**
     * 合成单条预处理文本。
     *
     * @param preparedTextEntity 预处理文本
     * @param endpointEnum 端点枚举
     * @param modelCode 模型编码
     * @param traceId 链路追踪标识
     * @return 音频实体
     */
    private TtsAudioEntity synthesizeSingleText(
        TtsPreparedTextEntity preparedTextEntity,
        TtsEndpointEnum endpointEnum,
        String modelCode,
        String traceId) {
        // 调用厂商 Feign 执行单条文本合成。
        TtsVendorResponseEntity vendorResponseEntity =
            ttsVendorFeignRepository.synthesize(endpointEnum, modelCode, preparedTextEntity, traceId);
        if (vendorResponseEntity == null || !StringUtils.hasText(vendorResponseEntity.getBase64Audio())) {
            ttsMetricsCollector.recordVendorEmptyAudio(endpointEnum.getCode());
            throw new BizException(ErrorCodeEnum.TTS_ALL_FAILED, VENDOR_RESPONSE_BASE64_EMPTY_MESSAGE);
        }

        // 将厂商结果转换为统一的领域音频实体。
        return buildAudioEntity(
            preparedTextEntity,
            vendorResponseEntity.getBase64Audio(),
            vendorResponseEntity.getVendorTraceId(),
            vendorResponseEntity.getSynthesizeText());
    }

    /**
     * 上传单条音频到 OSS。
     *
     * @param audioEntity 音频实体
     * @param traceId 链路追踪标识
     * @return 补齐 URL 后的音频实体
     */
    private TtsAudioEntity uploadSingleAudio(TtsAudioEntity audioEntity, String traceId) {
        // 使用最终文本和 Base64 上传到 OSS，获取持久化 URL。
        String audioUrl = ossStorageRepository.uploadBase64(
            audioEntity.getFinalText(),
            audioEntity.getBase64Audio(),
            traceId);
        return TtsAudioEntity.builder()
            .sequence(audioEntity.getSequence())
            .rawText(audioEntity.getRawText())
            .finalText(audioEntity.getFinalText())
            .synthesizeText(audioEntity.getSynthesizeText())
            .base64Audio(audioEntity.getBase64Audio())
            .audioUrl(audioUrl)
            .cacheKey(audioEntity.getCacheKey())
            .hasNameTag(audioEntity.getHasNameTag())
            .vendorTraceId(audioEntity.getVendorTraceId())
            .cacheHit(audioEntity.getCacheHit())
            .build();
    }

    /**
     * 持久化单条缓存记录。
     *
     * @param audioEntity 音频实体
     * @param modelCode 模型编码
     * @param brokerId 券商 ID
     */
    private void writeCache(TtsAudioEntity audioEntity, String modelCode, String brokerId) {
        // 正式缓存键必须基于最终文本构建，确保缓存与真实合成文本一致。
        String cacheKey = buildCacheKey(brokerId, modelCode, audioEntity.getFinalText());
        ttsCacheRepository.putAudioCache(
            brokerId,
            cacheKey,
            TtsAudioCacheEntity.builder()
                .base64Audio(audioEntity.getBase64Audio())
                .build());
    }

    /**
     * 异步上传音频并回填 URL 缓存。
     *
     * @param audioEntityList 音频列表
     * @param traceId 链路追踪标识
     * @param userId 用户 ID
     * @param brokerId 券商 ID
     * @param businessCode 业务标识
     */
    private void asyncUploadAudioAndCacheUrl(
        List<TtsAudioEntity> audioEntityList,
        String traceId,
        String userId,
        String brokerId,
        String businessCode) {
        // 逐条提交异步上传任务，已有 URL 缓存的音频会在提交前跳过。
        for (TtsAudioEntity audioEntity : audioEntityList) {
            submitAsyncUploadTask(audioEntity, traceId, userId, brokerId, businessCode);
        }
    }

    /**
     * 提交单条音频的异步上传任务。
     *
     * @param audioEntity 音频实体
     * @param traceId 链路追踪标识
     * @param userId 用户 ID
     * @param brokerId 券商 ID
     * @param businessCode 业务标识
     */
    private void submitAsyncUploadTask(
        TtsAudioEntity audioEntity,
        String traceId,
        String userId,
        String brokerId,
        String businessCode) {
        String cachedAudioUrl = audioEntity.getAudioUrl();
        if (!Boolean.TRUE.equals(audioEntity.getCacheHit())) {
            // 非缓存命中场景在异步上传前再查一次 URL 缓存，避免并发重复上传。
            cachedAudioUrl = findAudioUrlCache(brokerId, audioEntity.getCacheKey());
        }
        if (StringUtils.hasText(cachedAudioUrl)) {
            log.info(
                "音频已存在URL缓存，跳过异步上传，traceId={}, userId={}, brokerId={}, businessCode={}, audioUrl={}, synthesizeText={}",
                traceId,
                userId,
                brokerId,
                businessCode,
                cachedAudioUrl,
                audioEntity.getSynthesizeText());
            return;
        }

        CompletableFuture.runAsync(
                () -> uploadAudioAndCacheUrl(audioEntity, traceId, userId, brokerId, businessCode),
                ttsOssUploadExecutor)
            .exceptionally(exception -> {
                // 调度失败只记录日志，不影响同步主链路返回。
                log.error(
                    "异步上传音频任务提交失败，brokerId={}, businessCode={}, traceId={}, finalText={}",
                    brokerId,
                    businessCode,
                    traceId,
                    audioEntity.getFinalText(),
                    exception);
                return null;
            });
    }

    /**
     * 统一等待异步任务全部完成。
     *
     * @param futureList 异步任务列表
     */
    private static void waitAllFutures(List<? extends CompletableFuture<?>> futureList) {
        // 使用 allOf 统一等待，避免调用方逐个 join。
        CompletableFuture.allOf(futureList.toArray(CompletableFuture[]::new)).join();
    }

    /**
     * 上传单条音频并回填 URL 缓存。
     *
     * @param audioEntity 音频实体
     * @param traceId 链路追踪标识
     * @param userId 用户 ID
     * @param brokerId 券商 ID
     * @param businessCode 业务标识
     */
    private void uploadAudioAndCacheUrl(
        TtsAudioEntity audioEntity,
        String traceId,
        String userId,
        String brokerId,
        String businessCode) {
        try {
            // 先上传当前音频，再回填独立的 URL 缓存。
            TtsAudioEntity uploadedAudioEntity = uploadSingleAudio(audioEntity, traceId);
            putAudioUrlCache(uploadedAudioEntity, brokerId);
            ttsMetricsCollector.recordAsyncUploadSuccess(brokerId);
            log.info(
                "异步上传音频成功，traceId={}, userId={}, brokerId={}, businessCode={}, audioUrl={}, synthesizeText={}",
                traceId,
                userId,
                brokerId,
                businessCode,
                uploadedAudioEntity.getAudioUrl(),
                uploadedAudioEntity.getSynthesizeText());
        } catch (RuntimeException exception) {
            ttsMetricsCollector.recordAsyncUploadFailed(brokerId);
            log.error(
                "异步上传音频失败，brokerId={}, businessCode={}, traceId={}, finalText={}",
                brokerId,
                businessCode,
                traceId,
                audioEntity.getFinalText(),
                exception);
        }
    }

    /**
     * 写入音频 URL 缓存。
     *
     * @param audioEntity 音频实体
     * @param brokerId 券商 ID
     */
    private void putAudioUrlCache(TtsAudioEntity audioEntity, String brokerId) {
        if (!StringUtils.hasText(audioEntity.getCacheKey())) {
            return;
        }
        if (Boolean.TRUE.equals(audioEntity.getHasNameTag())) {
            return;
        }
        ttsCacheRepository.putAudioUrlCache(
            brokerId,
            buildUrlCacheKey(audioEntity.getCacheKey()),
            audioEntity.getAudioUrl());
    }

    /**
     * 根据 Base64 缓存键构建 URL 缓存键。
     *
     * @param base64CacheKey Base64 缓存键
     * @return URL 缓存键
     */
    private String buildUrlCacheKey(String base64CacheKey) {
        if (!StringUtils.hasText(base64CacheKey)) {
            return base64CacheKey;
        }

        String base64KeyPrefix = speechTtsProperties.getCache().getBase64KeyPrefix();
        String prefixWithSeparator = base64KeyPrefix + StringPool.COLON;
        if (!base64CacheKey.startsWith(prefixWithSeparator)) {
            return base64CacheKey;
        }

        return speechTtsProperties.getCache().getUrlKeyPrefix()
            + base64CacheKey.substring(base64KeyPrefix.length());
    }

    /**
     * 根据券商、模型和文本构建 Redis 缓存键。
     *
     * @param brokerId 券商 ID
     * @param modelCode 模型编码
     * @param finalText 最终文本
     * @return 缓存键
     */
    private String buildCacheKey(String brokerId, String modelCode, String finalText) {
        // 对最终文本做 MD5，避免直接使用超长文本作为 Redis key。
        String textMd5 = Md5Util.md5(finalText);
        String normalizedBrokerId = CommonConstant.UNKNOWN;
        if (StringUtils.hasText(brokerId)) {
            normalizedBrokerId = brokerId;
        }
        return String.join(
            StringPool.COLON,
            speechTtsProperties.getCache().getBase64KeyPrefix(),
            normalizedBrokerId,
            modelCode,
            textMd5);
    }

    /**
     * 校验单条文本长度。
     *
     * @param rawText 原始文本
     * @param brokerId 券商 ID
     */
    private void validateRawTextLength(String rawText, String brokerId) {
        Integer maxTextLength = speechTtsProperties.getMaxTextLength();
        if (maxTextLength == null || maxTextLength <= 0) {
            return;
        }
        if (rawText == null) {
            BizAssert.isTrue(false, ErrorCodeEnum.INVALID_PARAMETER, RAW_TEXT_EXCEEDS_MAX_TEXT_LENGTH_MESSAGE);
            return;
        }
        if (rawText.length() > maxTextLength) {
            ttsMetricsCollector.recordTextLengthLimitRejected(brokerId);
            BizAssert.isTrue(false, ErrorCodeEnum.INVALID_PARAMETER, RAW_TEXT_EXCEEDS_MAX_TEXT_LENGTH_MESSAGE);
        }
    }

    /**
     * 判断文本是否包含 name 标签。
     *
     * @param text 待判断文本
     * @return 是否包含 name 标签
     */
    private boolean containsNameTag(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return SsmlUtil.containsNameTag(
            text,
            speechTtsProperties.getNameTag().getNameTagStartTag(),
            speechTtsProperties.getNameTag().getNameTagEndTag(),
            speechTtsProperties.getNameTag().getCompatibleNameTagStartTagList());
    }

    /**
     * 标准化文本中的兼容 name 标签。
     *
     * @param text 原始文本
     * @return 标准化后的文本
     */
    private String normalizeNameTag(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        return SsmlUtil.normalizeNameTag(
            text,
            speechTtsProperties.getNameTag().getNameTagStartTag(),
            speechTtsProperties.getNameTag().getCompatibleNameTagStartTagList());
    }

    /**
     * 判断本批预处理结果是否全部命中缓存。
     *
     * @param preparedTextEntityList 预处理文本列表
     * @return 是否全部命中缓存
     */
    private static boolean isAllPreparedTextCacheHit(List<TtsPreparedTextEntity> preparedTextEntityList) {
        if (preparedTextEntityList.isEmpty()) {
            return false;
        }
        for (TtsPreparedTextEntity preparedTextEntity : preparedTextEntityList) {
            if (!Boolean.TRUE.equals(preparedTextEntity.getAudioCacheHit())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 将缓存命中的预处理结果转换为音频列表。
     *
     * @param preparedTextEntityList 预处理文本列表
     * @return 音频列表
     */
    private static List<TtsAudioEntity> buildCacheHitAudioList(List<TtsPreparedTextEntity> preparedTextEntityList) {
        List<TtsAudioEntity> audioEntityList = new ArrayList<>(preparedTextEntityList.size());
        for (TtsPreparedTextEntity preparedTextEntity : preparedTextEntityList) {
            audioEntityList.add(buildAudioEntity(
                preparedTextEntity,
                preparedTextEntity.getCachedBase64Audio(),
                null,
                preparedTextEntity.getFinalText()));
        }
        return audioEntityList;
    }


    /**
     * 构建预处理文本实体。
     *
     * @param sequence 顺序号
     * @param rawText 原始文本
     * @param finalText 最终文本
     * @param hasNameTag 是否包含 name 标签
     * @param cacheKey 缓存键
     * @param audioCacheHit 是否命中音频缓存
     * @param needSynthesize 是否需要合成
     * @return 预处理文本实体
     */
    private static TtsPreparedTextEntity buildPreparedTextEntity(
        Integer sequence,
        String rawText,
        String finalText,
        Boolean hasNameTag,
        String cacheKey,
        Boolean audioCacheHit,
        Boolean needSynthesize) {
        return TtsPreparedTextEntity.builder()
            .sequence(sequence)
            .rawText(rawText)
            .finalText(finalText)
            .hasNameTag(hasNameTag)
            .cacheKey(cacheKey)
            .audioCacheHit(audioCacheHit)
            .cachedAudioUrl(null)
            .needSynthesize(needSynthesize)
            .build();
    }

    /**
     * 构建领域音频实体。
     *
     * @param preparedTextEntity 预处理文本
     * @param base64Audio Base64 音频
     * @param vendorTraceId 厂商链路标识
     * @param synthesizeText 实际合成文本
     * @return 音频实体
     */
    private static TtsAudioEntity buildAudioEntity(
        TtsPreparedTextEntity preparedTextEntity,
        String base64Audio,
        String vendorTraceId,
        String synthesizeText) {
        return TtsAudioEntity.builder()
            .sequence(preparedTextEntity.getSequence())
            .rawText(preparedTextEntity.getRawText())
            .finalText(preparedTextEntity.getFinalText())
            .synthesizeText(synthesizeText)
            .base64Audio(base64Audio)
            .audioUrl(preparedTextEntity.getCachedAudioUrl())
            .cacheKey(preparedTextEntity.getCacheKey())
            .hasNameTag(preparedTextEntity.getHasNameTag())
            .vendorTraceId(vendorTraceId)
            .cacheHit(preparedTextEntity.getAudioCacheHit())
            .build();
    }
}
