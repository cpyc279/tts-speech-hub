package com.tts.speech.deploy.modules.tts.infrastructure.repository.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tts.speech.deploy.common.metrics.TtsMetricsCollector;
import com.tts.speech.deploy.common.redis.RedisOperator;
import com.tts.speech.deploy.common.util.JsonUtil;
import com.tts.speech.deploy.modules.tts.domain.entity.NerTemplateCacheEntity;
import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechNerProperties;
import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechTtsProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * NerTemplateCacheSupport 单元测试。
 *
 * @author yangchen5
 * @since 2026-03-28 15:30:00
 */
class NerTemplateCacheSupportTest {

    /**
     * 控制流关键字匹配表达式。
     */
    private static final Pattern CONTROL_FLOW_PATTERN = Pattern.compile("\\b(if|for|while|switch|try)\\b");

    /**
     * 允许的最大控制流嵌套深度。
     */
    private static final int MAX_ALLOWED_CONTROL_FLOW_DEPTH = 3;

    /**
     * 源码文件路径。
     */
    private static final Path SOURCE_FILE_PATH = Path.of(
        "src",
        "main",
        "java",
        "com",
        "tts",
        "speech",
        "deploy",
        "modules",
        "tts",
        "infrastructure",
        "repository",
        "impl",
        "NerTemplateCacheSupport.java");

    /**
     * 目标方法签名。
     */
    private static final String MATCH_TEMPLATE_METHOD_SIGNATURE =
        "public Optional<String> matchAvailableTemplate(String brokerId, String rawText, String traceId)";

    /**
     * 验证模板命中后会返回带名称标签的文本，并刷新缓存命中次数。
     */
    @Test
    void testMatchAvailableTemplateShouldReturnWrappedTextWhenTemplateHit() {
        RedisOperator redisOperator = Mockito.mock(RedisOperator.class);
        TtsMetricsCollector ttsMetricsCollector = Mockito.mock(TtsMetricsCollector.class);
        NerTemplateCacheSupport cacheSupport = createCacheSupport(redisOperator, ttsMetricsCollector);
        NerTemplateCacheEntity templateEntity = NerTemplateCacheEntity.builder()
            .templateId("template-1")
            .templatePattern("你好(.+?)")
            .useCount(2)
            .lastMatchedAt(System.currentTimeMillis())
            .build();
        // 模拟 Redis 中已经存在达到命中阈值的成熟模板。
        Mockito.when(redisOperator.getHashEntries(
            Mockito.eq("ner_template_read"),
            Mockito.eq("tts:ner:template:broker-a"),
            Mockito.<String, String>anyMap(),
            Mockito.anyString(),
            Mockito.eq("broker-a"),
            Mockito.eq("tts:ner:template:broker-a"))).thenReturn(Map.of(
                "template-1",
                JsonUtil.toJson(templateEntity)));
        Mockito.when(redisOperator.putHashValue(
            Mockito.eq("ner_template_write"),
            Mockito.eq("tts:ner:template:broker-a"),
            Mockito.eq("template-1"),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.eq("broker-a"),
            Mockito.eq("template-1"))).thenReturn(true);

        Optional<String> matchedTextOptional = cacheSupport.matchAvailableTemplate("broker-a", "你好张三", "trace-1");

        Assertions.assertTrue(matchedTextOptional.isPresent());
        Assertions.assertEquals("<speak>你好<name>张三</name></speak>", matchedTextOptional.get());
        ArgumentCaptor<String> cacheValueCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(redisOperator).putHashValue(
            Mockito.eq("ner_template_write"),
            Mockito.eq("tts:ner:template:broker-a"),
            Mockito.eq("template-1"),
            cacheValueCaptor.capture(),
            Mockito.anyString(),
            Mockito.eq("broker-a"),
            Mockito.eq("template-1"));
        NerTemplateCacheEntity refreshedTemplateEntity = JsonUtil.fromJson(
            cacheValueCaptor.getValue(),
            new TypeReference<NerTemplateCacheEntity>() {
            });
        Assertions.assertEquals(3, refreshedTemplateEntity.getUseCount());
        Mockito.verify(ttsMetricsCollector).recordNerTemplateHit("broker-a");
    }

    /**
     * 验证目标方法的控制流嵌套深度不超过静态扫描阈值。
     *
     * @throws IOException 读取源码失败
     */
    @Test
    void testMatchAvailableTemplateShouldLimitControlFlowDepth() throws IOException {
        String methodSource = readMethodSource(MATCH_TEMPLATE_METHOD_SIGNATURE);

        int maxControlFlowDepth = calculateMaxControlFlowDepth(methodSource);

        Assertions.assertTrue(
            maxControlFlowDepth <= MAX_ALLOWED_CONTROL_FLOW_DEPTH,
            "matchAvailableTemplate 控制流嵌套深度过深，当前深度=" + maxControlFlowDepth);
    }

    /**
     * 创建模板缓存支持组件。
     *
     * @param redisOperator Redis 操作组件
     * @param ttsMetricsCollector 指标采集器
     * @return 模板缓存支持组件
     */
    private static NerTemplateCacheSupport createCacheSupport(
        RedisOperator redisOperator,
        TtsMetricsCollector ttsMetricsCollector) {
        NerTemplateCacheSupport cacheSupport = new NerTemplateCacheSupport();
        // 单测只注入命中路径需要的最小依赖，避免引入无关 Spring 上下文。
        ReflectionTestUtils.setField(cacheSupport, "redisOperator", redisOperator);
        ReflectionTestUtils.setField(cacheSupport, "speechNerProperties", buildSpeechNerProperties());
        ReflectionTestUtils.setField(cacheSupport, "speechTtsProperties", buildSpeechTtsProperties());
        ReflectionTestUtils.setField(cacheSupport, "ttsMetricsCollector", ttsMetricsCollector);
        return cacheSupport;
    }

    /**
     * 构建 NER 配置。
     *
     * @return NER 配置
     */
    private static SpeechNerProperties buildSpeechNerProperties() {
        SpeechNerProperties speechNerProperties = new SpeechNerProperties();
        SpeechNerProperties.TemplateConfig templateConfig = new SpeechNerProperties.TemplateConfig();
        // 命中阈值设置为 1，确保测试模板可直接参与匹配。
        templateConfig.setMatchThreshold(1);
        speechNerProperties.setTemplate(templateConfig);
        return speechNerProperties;
    }

    /**
     * 构建 TTS 配置。
     *
     * @return TTS 配置
     */
    private static SpeechTtsProperties buildSpeechTtsProperties() {
        SpeechTtsProperties speechTtsProperties = new SpeechTtsProperties();
        SpeechTtsProperties.NameTagConfig nameTagConfig = new SpeechTtsProperties.NameTagConfig();
        nameTagConfig.setNameTagStartTag("<name>");
        nameTagConfig.setNameTagEndTag("</name>");
        speechTtsProperties.setNameTag(nameTagConfig);
        SpeechTtsProperties.SsmlConfig ssmlConfig = new SpeechTtsProperties.SsmlConfig();
        ssmlConfig.setSpeakStartTag("<speak>");
        ssmlConfig.setSpeakEndTag("</speak>");
        speechTtsProperties.setSsml(ssmlConfig);
        return speechTtsProperties;
    }

    /**
     * 读取指定方法源码。
     *
     * @param methodSignature 方法签名
     * @return 方法源码
     * @throws IOException 读取源码失败
     */
    private static String readMethodSource(String methodSignature) throws IOException {
        String sourceCode = Files.readString(SOURCE_FILE_PATH);
        int methodSignatureIndex = sourceCode.indexOf(methodSignature);
        Assertions.assertTrue(methodSignatureIndex >= 0, "未找到目标方法签名");
        int methodBodyStartIndex = sourceCode.indexOf('{', methodSignatureIndex);
        Assertions.assertTrue(methodBodyStartIndex >= 0, "未找到目标方法起始大括号");
        int methodBodyEndIndex = findMethodBodyEndIndex(sourceCode, methodBodyStartIndex);
        return sourceCode.substring(methodSignatureIndex, methodBodyEndIndex + 1);
    }

    /**
     * 查找方法结束大括号位置。
     *
     * @param sourceCode 源码全文
     * @param methodBodyStartIndex 方法起始大括号位置
     * @return 方法结束大括号位置
     */
    private static int findMethodBodyEndIndex(String sourceCode, int methodBodyStartIndex) {
        int braceDepth = 0;
        for (int index = methodBodyStartIndex; index < sourceCode.length(); index++) {
            char currentChar = sourceCode.charAt(index);
            if (currentChar == '{') {
                braceDepth++;
                continue;
            }
            if (currentChar == '}') {
                braceDepth--;
                if (braceDepth == 0) {
                    return index;
                }
            }
        }
        Assertions.fail("未找到目标方法结束大括号");
        return -1;
    }

    /**
     * 计算方法的最大控制流嵌套深度。
     *
     * @param methodSource 方法源码
     * @return 最大嵌套深度
     */
    private static int calculateMaxControlFlowDepth(String methodSource) {
        Deque<Boolean> blockTypeStack = new ArrayDeque<>();
        Deque<Boolean> pendingControlFlowStack = new ArrayDeque<>();
        int currentControlFlowDepth = 0;
        int maxControlFlowDepth = 0;
        String[] sourceLineArray = methodSource.split("\\R");
        for (String sourceLine : sourceLineArray) {
            String trimmedLine = sourceLine.trim();
            if (shouldSkipLine(trimmedLine)) {
                continue;
            }
            collectPendingControlFlow(trimmedLine, pendingControlFlowStack);
            for (int index = 0; index < trimmedLine.length(); index++) {
                char currentChar = trimmedLine.charAt(index);
                if (currentChar == '{') {
                    boolean isControlFlowBlock = !pendingControlFlowStack.isEmpty() && pendingControlFlowStack.pop();
                    blockTypeStack.push(isControlFlowBlock);
                    if (isControlFlowBlock) {
                        currentControlFlowDepth++;
                        maxControlFlowDepth = Math.max(maxControlFlowDepth, currentControlFlowDepth);
                    }
                    continue;
                }
                if (currentChar == '}') {
                    popControlFlowDepth(blockTypeStack, currentControlFlowDepth);
                    if (!blockTypeStack.isEmpty()) {
                        boolean isControlFlowBlock = blockTypeStack.pop();
                        if (isControlFlowBlock) {
                            currentControlFlowDepth--;
                        }
                    }
                }
            }
        }
        return maxControlFlowDepth;
    }

    /**
     * 判断当前行是否应跳过。
     *
     * @param trimmedLine 去除首尾空白后的源码行
     * @return 是否跳过
     */
    private static boolean shouldSkipLine(String trimmedLine) {
        if (trimmedLine.isEmpty()) {
            return true;
        }
        if (trimmedLine.startsWith("//")) {
            return true;
        }
        if (trimmedLine.startsWith("/*")) {
            return true;
        }
        return trimmedLine.startsWith("*");
    }

    /**
     * 收集待入栈的控制流块。
     *
     * @param trimmedLine 去除首尾空白后的源码行
     * @param pendingControlFlowStack 待入栈控制流标记
     */
    private static void collectPendingControlFlow(String trimmedLine, Deque<Boolean> pendingControlFlowStack) {
        Matcher matcher = CONTROL_FLOW_PATTERN.matcher(trimmedLine);
        while (matcher.find()) {
            pendingControlFlowStack.push(true);
        }
    }

    /**
     * 弹出控制流深度。
     *
     * @param blockTypeStack 块类型栈
     * @param currentControlFlowDepth 当前控制流深度
     */
    private static void popControlFlowDepth(Deque<Boolean> blockTypeStack, int currentControlFlowDepth) {
        Assertions.assertTrue(
            currentControlFlowDepth >= 0,
            "控制流深度计算出现负值，说明源码解析逻辑异常");
        Assertions.assertFalse(blockTypeStack.isEmpty(), "源码块类型栈为空，说明源码解析逻辑异常");
    }
}
