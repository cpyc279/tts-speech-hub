package com.tts.speech.deploy.modules.tts.infrastructure.repository.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tts.speech.deploy.common.constant.CommonConstant;
import com.tts.speech.deploy.common.constant.StringPool;
import com.tts.speech.deploy.common.metrics.TtsMetricsCollector;
import com.tts.speech.deploy.common.redis.RedisOperator;
import com.tts.speech.deploy.common.util.JsonUtil;
import com.tts.speech.deploy.common.util.Md5Util;
import com.tts.speech.deploy.modules.tts.domain.entity.NerTemplateCacheEntity;
import com.tts.speech.deploy.modules.tts.infrastructure.cache.BrokerScopedCacheEvictionSupport;
import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechNerProperties;
import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechTtsProperties;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * NER 模板缓存支持组件。
 *
 * @author yangchen5
 * @since 2026-03-28 12:00:00
 */
@Slf4j
@Component
public class NerTemplateCacheSupport {

    /**
     * Redis 模板读取操作名。
     */
    private static final String REDIS_OPERATION_NER_TEMPLATE_READ = "ner_template_read";

    /**
     * Redis 模板写入操作名。
     */
    private static final String REDIS_OPERATION_NER_TEMPLATE_WRITE = "ner_template_write";

    /**
     * Redis 模板删除操作名。
     */
    private static final String REDIS_OPERATION_NER_TEMPLATE_DELETE = "ner_template_delete";

    /**
     * 模板占位符。
     */
    private static final String PLACEHOLDER = "\u0000";

    /**
     * 模板人名捕获表达式替换片段。
     */
    private static final String TEMPLATE_CAPTURE_REPLACEMENT = "\\E(.+?)\\Q";

    @Resource
    private RedisOperator redisOperator;

    @Resource
    private SpeechNerProperties speechNerProperties;

    @Resource
    private SpeechTtsProperties speechTtsProperties;

    @Resource
    private TtsMetricsCollector ttsMetricsCollector;

    /**
     * 按券商维度匹配可直接使用的成熟模板。
     *
     * @param brokerId 券商编号
     * @param rawText 原始文本
     * @param traceId 链路追踪标识
     * @return 模板命中的最终文本
     */
    public Optional<String> matchAvailableTemplate(String brokerId, String rawText, String traceId) {
        try {
            // 将匹配主流程下沉到私有方法，避免公共入口方法出现过深的控制流嵌套。
            return doMatchAvailableTemplate(brokerId, rawText, traceId);
        } catch (RuntimeException exception) {
            // 模板匹配属于优化路径，异常时直接降级为继续调用 NER。
            log.warn("NER模板匹配失败，继续调用NER服务，brokerId={}, traceId={}, rawText={}",
                brokerId, traceId, rawText, exception);
        }
        return Optional.empty();
    }

    /**
     * 执行模板匹配主流程。
     *
     * @param brokerId 券商编号
     * @param rawText 原始文本
     * @param traceId 链路追踪标识
     * @return 匹配结果
     */
    private Optional<String> doMatchAvailableTemplate(String brokerId, String rawText, String traceId) {
        // 所有模板都基于归一化后的 brokerId 做隔离加载，避免空值影响缓存分区。
        String normalizedBrokerId = normalizeBrokerId(brokerId);
        String templateKey = buildTemplateKey(normalizedBrokerId);
        // 只加载已达到命中阈值的成熟模板，减少无效逐个匹配。
        List<NerTemplateCacheEntity> availableTemplateList =
            loadAvailableTemplateList(normalizedBrokerId);
        for (NerTemplateCacheEntity templateEntity : availableTemplateList) {
            Optional<String> matchedFinalTextOptional =
                matchSingleTemplate(rawText, templateEntity.getTemplatePattern());
            if (matchedFinalTextOptional.isEmpty()) {
                continue;
            }
            // 命中后立即刷新缓存状态并返回结果，避免后续继续遍历模板。
            return handleMatchedTemplate(
                brokerId,
                traceId,
                normalizedBrokerId,
                templateKey,
                templateEntity,
                matchedFinalTextOptional.get());
        }
        return Optional.empty();
    }

    /**
     * 处理模板命中后的刷新、指标和日志逻辑。
     *
     * @param brokerId 券商编号
     * @param traceId 链路追踪标识
     * @param normalizedBrokerId 归一化后的券商编号
     * @param templateKey 模板缓存 key
     * @param templateEntity 命中模板实体
     * @param matchedFinalText 命中后的最终文本
     * @return 命中结果
     */
    private Optional<String> handleMatchedTemplate(
        String brokerId,
        String traceId,
        String normalizedBrokerId,
        String templateKey,
        NerTemplateCacheEntity templateEntity,
        String matchedFinalText) {
        // 模板命中后刷新命中次数和最近访问时间。
        refreshTemplate(templateEntity, normalizedBrokerId);
        // 记录模板命中指标，便于观察模板复用收益。
        ttsMetricsCollector.recordNerTemplateHit(brokerId);
        // 模板命中时打印 redis key，便于排查模板缓存复用。
        log.info(
            "NER成熟模板缓存命中，{}",
            buildTemplateHitLog(brokerId, traceId, templateKey, templateEntity));
        return Optional.of(matchedFinalText);
    }

    /**
     * 构建模板命中日志内容。
     *
     * @param brokerId 券商编号
     * @param traceId 链路追踪标识
     * @param templateKey 模板缓存 key
     * @param templateEntity 命中模板实体
     * @return 日志内容
     */
    private static String buildTemplateHitLog(
        String brokerId,
        String traceId,
        String templateKey,
        NerTemplateCacheEntity templateEntity) {
        StringJoiner logJoiner = new StringJoiner(StringPool.COMMA + StringPool.SPACE);
        logJoiner.add("traceId=" + traceId);
        if (StringUtils.hasText(brokerId)) {
            logJoiner.add("brokerId=" + brokerId);
        }
        logJoiner.add("templateKey=" + templateKey);
        logJoiner.add("templateId=" + templateEntity.getTemplateId());
        logJoiner.add("useCount=" + templateEntity.getUseCount());
        return logJoiner.toString();
    }

    /**
     * 合并模板命中次数并刷新模板活跃时间。
     *
     * @param brokerId 券商编号
     * @param rawText 原始文本
     * @param peopleNameList 人名列表
     * @param traceId 链路追踪标识
     */
    public void mergeTemplate(String brokerId, String rawText, List<String> peopleNameList, String traceId) {
        if (CollectionUtils.isEmpty(peopleNameList)) {
            return;
        }
        try {
            // 所有模板缓存都按归一化后的 brokerId 隔离。
            String normalizedBrokerId = normalizeBrokerId(brokerId);
            // 基于原文和人名列表生成模板正则，同句式文本会映射到同一模板。
            String templatePattern = buildTemplatePattern(rawText, peopleNameList);
            String templateId = buildTemplateId(templatePattern);
            // 读取模板前先做过期清理，避免老模板影响后续判断。
            Map<String, NerTemplateCacheEntity> templateMap = readTemplateMap(normalizedBrokerId);
            removeExpiredTemplate(normalizedBrokerId, templateMap);
            NerTemplateCacheEntity existingTemplateEntity = templateMap.get(templateId);
            // 模板已存在则次数加一，不存在则从 1 开始累计。
            NerTemplateCacheEntity mergedTemplateEntity = buildMergedTemplateEntity(
                existingTemplateEntity,
                templateId,
                templatePattern);
            // 新模板写入前先按最近未使用原则淘汰最旧模板，避免缓存超限。
            trimTemplateCountBeforeInsertIfNecessary(
                normalizedBrokerId,
                traceId,
                templateId,
                templateMap,
                existingTemplateEntity);
            // 把合并后的模板元数据重新写回 Redis。
            boolean writeSuccess = writeTemplate(normalizedBrokerId, mergedTemplateEntity);
            if (!writeSuccess) {
                // 模板写入失败时递增失败指标，便于观察模板缓存异常频次。
                ttsMetricsCollector.recordTemplateMergeFailed(brokerId);
                return;
            }
            log.info("NER模板计数更新完成，brokerId={}, traceId={}, templateId={}, useCount={}",
                brokerId, traceId, mergedTemplateEntity.getTemplateId(), mergedTemplateEntity.getUseCount());
        } catch (RuntimeException exception) {
            // 模板写入失败时递增失败指标，便于观察模板缓存异常频次。
            ttsMetricsCollector.recordTemplateMergeFailed(brokerId);
            // 模板写入失败不影响主链路，只记录告警。
            log.warn("NER模板写入失败，忽略模板更新，brokerId={}, traceId={}, rawText={}",
                brokerId, traceId, rawText, exception);
        }
    }

    /**
     * 加载可直接参与匹配的模板列表。
     *
     * @param normalizedBrokerId 归一化后的券商编号
     * @return 模板列表
     */
    private List<NerTemplateCacheEntity> loadAvailableTemplateList(String normalizedBrokerId) {
        // 先读取当前券商的全部模板数据。
        Map<String, NerTemplateCacheEntity> templateMap = readTemplateMap(normalizedBrokerId);
        // 读取阶段顺手清理过期模板，避免缓存持续膨胀。
        removeExpiredTemplate(normalizedBrokerId, templateMap);
        // 只有达到匹配阈值的成熟模板才允许直接参与匹配。
        return templateMap.values().stream()
            .filter(this::isReachMatchThreshold)
            .sorted(buildAvailableTemplateComparator())
            .toList();
    }

    /**
     * 构建成熟模板排序规则。
     *
     * @return 排序规则
     */
    private static Comparator<NerTemplateCacheEntity> buildAvailableTemplateComparator() {
        // 先按使用次数，再按最近命中时间排序。
        return Comparator.comparing(NerTemplateCacheEntity::getUseCount, Comparator.reverseOrder())
            .thenComparing(NerTemplateCacheEntity::getLastMatchedAt, Comparator.reverseOrder());
    }

    /**
     * 判断模板是否达到匹配阈值。
     *
     * @param templateEntity 模板实体
     * @return 是否达到阈值
     */
    private boolean isReachMatchThreshold(NerTemplateCacheEntity templateEntity) {
        Integer matchThreshold = speechNerProperties.getTemplate().getMatchThreshold();
        if (matchThreshold == null) {
            return false;
        }
        // 只有命中次数达到阈值后，模板才允许直接参与匹配。
        return templateEntity.getUseCount() != null && templateEntity.getUseCount() >= matchThreshold;
    }

    /**
     * 移除过期模板。
     *
     * @param normalizedBrokerId 归一化后的券商编号
     * @param templateMap 模板映射
     */
    private void removeExpiredTemplate(String normalizedBrokerId, Map<String, NerTemplateCacheEntity> templateMap) {
        // 统一取当前时间做比较，避免同一轮判断出现时间漂移。
        long currentTimestamp = System.currentTimeMillis();
        List<String> expiredTemplateIdList = BrokerScopedCacheEvictionSupport.collectExpiredEntryIdList(
            templateMap,
            templateEntity -> isExpiredTemplate(templateEntity, currentTimestamp),
            NerTemplateCacheEntity::getTemplateId);
        if (CollectionUtils.isEmpty(expiredTemplateIdList)) {
            return;
        }
        // 先从 Redis 中删除过期模板，避免缓存继续膨胀。
        deleteTemplateEntries(normalizedBrokerId, expiredTemplateIdList);
        // 无论 Redis 删除是否成功，本轮内存视图都移除过期模板。
        expiredTemplateIdList.forEach(templateMap::remove);
    }

    /**
     * 判断模板是否过期。
     *
     * @param templateEntity 模板实体
     * @param currentTimestamp 当前时间戳
     * @return 是否过期
     */
    private boolean isExpiredTemplate(NerTemplateCacheEntity templateEntity, long currentTimestamp) {
        Long ttlDays = speechNerProperties.getTemplate().getTtlDays();
        if (ttlDays == null || ttlDays <= 0 || templateEntity.getLastMatchedAt() == null) {
            return false;
        }
        // 单模板过期按“最后一次命中时间 + TTL天数”规则做懒清理。
        Duration ttlDuration = Duration.ofDays(ttlDays);
        return currentTimestamp - templateEntity.getLastMatchedAt() > ttlDuration.toMillis();
    }

    /**
     * 读取模板映射。
     *
     * @param normalizedBrokerId 归一化后的券商编号
     * @return 模板映射
     */
    private Map<String, NerTemplateCacheEntity> readTemplateMap(String normalizedBrokerId) {
        // 模板缓存按 Hash 存储，field 是 templateId，value 是模板元数据 JSON。
        Map<String, String> templateValueMap = redisOperator.getHashEntries(
            REDIS_OPERATION_NER_TEMPLATE_READ,
            buildTemplateKey(normalizedBrokerId),
            Collections.emptyMap(),
            "读取NER模板缓存失败，brokerId={}, templateKey={}",
            normalizedBrokerId,
            buildTemplateKey(normalizedBrokerId));
        if (CollectionUtils.isEmpty(templateValueMap)) {
            return new LinkedHashMap<>();
        }
        // 反序列化失败的模板直接跳过，不影响其他模板继续参与流程。
        return templateValueMap.entrySet().stream()
            .map(NerTemplateCacheSupport::deserializeTemplateEntry)
            .flatMap(Optional::stream)
            .collect(
                LinkedHashMap::new,
                (templateMap, templateEntity) -> templateMap.put(templateEntity.getTemplateId(), templateEntity),
                LinkedHashMap::putAll);
    }

    /**
     * 反序列化模板条目。
     *
     * @param templateEntry 模板缓存条目
     * @return 模板实体
     */
    private static Optional<NerTemplateCacheEntity> deserializeTemplateEntry(Map.Entry<String, String> templateEntry) {
        try {
            // 先把模板 JSON 反序列化，再把 field 里的 templateId 回填到实体中。
            NerTemplateCacheEntity templateEntity = JsonUtil.fromJson(
                templateEntry.getValue(),
                new TypeReference<NerTemplateCacheEntity>() {
                });
            templateEntity.setTemplateId(templateEntry.getKey());
            return Optional.of(templateEntity);
        } catch (IllegalStateException exception) {
            // 单条模板损坏只跳过当前记录，不影响整批模板读取。
            log.warn("NER模板缓存反序列化失败，templateId={}", templateEntry.getKey(), exception);
            return Optional.empty();
        }
    }

    /**
     * 匹配单个模板。
     *
     * @param rawText 原始文本
     * @param templatePattern 模板正则
     * @return 命中的最终文本
     */
    private Optional<String> matchSingleTemplate(String rawText, String templatePattern) {
        if (!StringUtils.hasText(templatePattern)) {
            return Optional.empty();
        }
        // 使用全量 matches 匹配，只有句式完全一致时才算命中模板。
        Matcher matcher = Pattern.compile(templatePattern).matcher(rawText);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        // 把捕获组中的动态内容按顺序提取出来，作为人名列表。
        List<String> peopleNameList = extractMatchedPeopleNames(matcher);
        if (CollectionUtils.isEmpty(peopleNameList)) {
            return Optional.empty();
        }
        // 模板命中后直接生成带 nameTag 的最终文本，跳过远程 NER 调用。
        return Optional.of(wrapTextWithNameTags(rawText, peopleNameList));
    }

    /**
     * 提取模板命中的人名列表。
     *
     * @param matcher 正则匹配器
     * @return 人名列表
     */
    private static List<String> extractMatchedPeopleNames(Matcher matcher) {
        // 所有捕获组都视为模板中的动态人名片段，按顺序回填即可。
        return IntStream.rangeClosed(1, matcher.groupCount())
            .mapToObj(matcher::group)
            .filter(StringUtils::hasText)
            .toList();
    }

    /**
     * 根据原始文本和人名列表构建模板正则。
     *
     * @param rawText 原始文本
     * @param peopleNameList 人名列表
     * @return 正则模板
     */
    private static String buildTemplatePattern(String rawText, List<String> peopleNameList) {
        // 先把所有人名替换成占位符，得到稳定的句式骨架。
        String placeholderText = peopleNameList.stream()
            .reduce(rawText, (text, peopleName) -> text.replace(peopleName, PLACEHOLDER), (left, right) -> right);
        // 再把占位符转成非贪婪捕获组，形成可回填人名的正则模板。
        return Pattern.quote(placeholderText).replace(PLACEHOLDER, TEMPLATE_CAPTURE_REPLACEMENT);
    }

    /**
     * 给文本中的人名补充 name 标签，并按需补齐 speak 标签。
     *
     * @param rawText 原始文本
     * @param peopleNameList 人名列表
     * @return 处理后的文本
     */
    private String wrapTextWithNameTags(String rawText, List<String> peopleNameList) {
        String nameTagStart = speechTtsProperties.getNameTag().getNameTagStartTag();
        String nameTagEnd = speechTtsProperties.getNameTag().getNameTagEndTag();
        // 逐个把识别出的人名替换为带 nameTag 的文本片段。
        String wrappedText = peopleNameList.stream()
            .reduce(
                rawText,
                (text, peopleName) -> text.replace(peopleName, nameTagStart + peopleName + nameTagEnd),
                (left, right) -> right);
        String speakStartTag = speechTtsProperties.getSsml().getSpeakStartTag();
        String speakEndTag = speechTtsProperties.getSsml().getSpeakEndTag();
        // 文本缺少 speak 外层标签时，在这里统一补齐。
        if (!wrappedText.contains(speakStartTag) || !wrappedText.contains(speakEndTag)) {
            return speakStartTag + wrappedText + speakEndTag;
        }
        return wrappedText;
    }

    /**
     * 刷新模板命中计数。
     *
     * @param templateEntity 模板实体
     * @param normalizedBrokerId 归一化后的券商编号
     */
    private void refreshTemplate(NerTemplateCacheEntity templateEntity, String normalizedBrokerId) {
        // 模板直接命中时，使用次数递增，同时刷新最近命中时间。
        NerTemplateCacheEntity refreshedTemplateEntity = NerTemplateCacheEntity.builder()
            .templateId(templateEntity.getTemplateId())
            .templatePattern(templateEntity.getTemplatePattern())
            .useCount(templateEntity.getUseCount() + 1)
            .lastMatchedAt(System.currentTimeMillis())
            .build();
        writeTemplate(normalizedBrokerId, refreshedTemplateEntity);
    }

    /**
     * 构建合并后的模板实体。
     *
     * @param existingTemplateEntity 已有模板
     * @param templateId 模板 ID
     * @param templatePattern 模板正则
     * @return 模板实体
     */
    private static NerTemplateCacheEntity buildMergedTemplateEntity(
        NerTemplateCacheEntity existingTemplateEntity,
        String templateId,
        String templatePattern) {
        Integer useCount = 1;
        // 模板已存在则累计次数，不存在则从 1 开始冷启动。
        if (existingTemplateEntity != null && existingTemplateEntity.getUseCount() != null) {
            useCount = existingTemplateEntity.getUseCount() + 1;
        }
        return NerTemplateCacheEntity.builder()
            .templateId(templateId)
            .templatePattern(templatePattern)
            .useCount(useCount)
            .lastMatchedAt(System.currentTimeMillis())
            .build();
    }

    /**
     * 写入模板缓存。
     *
     * @param normalizedBrokerId 归一化后的券商编号
     * @param templateEntity 模板实体
     * @return 是否写入成功
     */
    private boolean writeTemplate(String normalizedBrokerId, NerTemplateCacheEntity templateEntity) {
        // 模板元数据统一以 JSON 形式写入 Redis，失败时由 Redis 组件统一记录指标和异常。
        return redisOperator.putHashValue(
            REDIS_OPERATION_NER_TEMPLATE_WRITE,
            buildTemplateKey(normalizedBrokerId),
            templateEntity.getTemplateId(),
            JsonUtil.toJson(templateEntity),
            "写入NER模板缓存失败，brokerId={}, templateId={}",
            normalizedBrokerId,
            templateEntity.getTemplateId());
    }

    /**
     * 新模板写入前按最近未使用原则淘汰最旧模板。
     *
     * @param normalizedBrokerId 归一化后的券商编号
     * @param traceId 链路追踪标识
     * @param incomingTemplateId 待写入模板 ID
     * @param templateMap 模板映射
     * @param existingTemplateEntity 已存在模板
     */
    private void trimTemplateCountBeforeInsertIfNecessary(
        String normalizedBrokerId,
        String traceId,
        String incomingTemplateId,
        Map<String, NerTemplateCacheEntity> templateMap,
        NerTemplateCacheEntity existingTemplateEntity) {
        Integer maxTemplateCountPerBroker = speechNerProperties.getTemplate().getMaxTemplateCountPerBroker();
        if (maxTemplateCountPerBroker == null || maxTemplateCountPerBroker <= 0) {
            return;
        }
        // 已存在模板只更新使用次数，不参与淘汰判断。
        if (existingTemplateEntity != null) {
            return;
        }
        // 模板数量未达到上限时，允许直接插入新模板。
        if (templateMap.size() < maxTemplateCountPerBroker) {
            return;
        }
        NerTemplateCacheEntity removedTemplateEntity = BrokerScopedCacheEvictionSupport.findLeastRecentlyUsedEntry(
            templateMap.values(),
            NerTemplateCacheEntity::getLastMatchedAt,
            NerTemplateCacheEntity::getTemplateId);
        if (removedTemplateEntity == null) {
            return;
        }
        // 命中模板容量上限并执行 LRU 淘汰时打印关键上下文，便于排查模板被动清理原因。
        log.info(
            "NER模板缓存数量达到上限，执行最近最少使用淘汰，brokerId={}, traceId={}, incomingTemplateId={}, "
                + "evictedTemplateId={}, currentCount={}, maxCount={}",
            normalizedBrokerId,
            traceId,
            incomingTemplateId,
            removedTemplateEntity.getTemplateId(),
            templateMap.size(),
            maxTemplateCountPerBroker);
        // 先删除最久未使用模板，再给新模板腾空间。
        deleteTemplateEntries(normalizedBrokerId, List.of(removedTemplateEntity.getTemplateId()));
        // 本轮内存视图也同步移除，避免后续逻辑继续把它当成存量模板。
        templateMap.remove(removedTemplateEntity.getTemplateId());
    }

    /**
     * 删除模板条目。
     *
     * @param normalizedBrokerId 归一化后的券商编号
     * @param templateIdList 模板 ID 列表
     */
    private void deleteTemplateEntries(String normalizedBrokerId, List<String> templateIdList) {
        if (CollectionUtils.isEmpty(templateIdList)) {
            return;
        }
        // Redis 可用时批量删除模板条目，失败时由 Redis 组件统一降级处理。
        redisOperator.deleteHashFields(
            REDIS_OPERATION_NER_TEMPLATE_DELETE,
            buildTemplateKey(normalizedBrokerId),
            templateIdList.toArray(),
            0L,
            "删除NER模板缓存失败，brokerId={}, templateIdList={}",
            normalizedBrokerId,
            templateIdList);
    }

    /**
     * 归一化券商编号。
     *
     * @param brokerId 原始券商编号
     * @return 归一化后的券商编号
     */
    private static String normalizeBrokerId(String brokerId) {
        if (StringUtils.hasText(brokerId)) {
            return brokerId;
        }
        // brokerId 为空时统一归一为 unknown，避免出现空缓存分区。
        return CommonConstant.UNKNOWN;
    }

    /**
     * 构建模板缓存 key。
     *
     * @param normalizedBrokerId 归一化后的券商编号
     * @return 模板缓存 key
     */
    private static String buildTemplateKey(String normalizedBrokerId) {
        // 模板缓存统一使用“前缀:brokerId”的结构组织。
        return CommonConstant.NER_TEMPLATE_KEY_PREFIX + StringPool.COLON + normalizedBrokerId;
    }

    /**
     * 构建模板 ID。
     *
     * @param templatePattern 模板正则
     * @return 模板 ID
     */
    private static String buildTemplateId(String templatePattern) {
        // 模板 ID 使用正则内容的 MD5，保证相同模板可以稳定去重。
        return Md5Util.md5(templatePattern);
    }
}
