package com.tts.speech.deploy.modules.tts.infrastructure.repository.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tts.speech.deploy.common.enums.ErrorCodeEnum;
import com.tts.speech.deploy.common.enums.TtsEndpointEnum;
import com.tts.speech.deploy.common.exception.BizException;
import com.tts.speech.deploy.modules.tts.domain.constant.TtsVendorConstant;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsPreparedTextEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsVendorResponseEntity;
import com.tts.speech.deploy.modules.tts.domain.repository.TtsVendorAuthRepository;
import com.tts.speech.deploy.modules.tts.infrastructure.config.SpeechTtsProperties;
import com.tts.speech.deploy.modules.tts.infrastructure.external.dto.ThsLargeTtsReqDTO;
import com.tts.speech.deploy.modules.tts.infrastructure.external.dto.ThsLargeTtsRespDTO;
import com.tts.speech.deploy.modules.tts.infrastructure.external.dto.ThsSmallTtsRespDTO;
import com.tts.speech.deploy.modules.tts.infrastructure.external.feign.ThsTtsLargeFeignClient;
import com.tts.speech.deploy.modules.tts.infrastructure.external.feign.ThsTtsSmallFeignClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * TtsVendorFeignRepositoryImpl 单元测试。
 *
 * @author yangchen5
 * @since 2026-03-18 00:00:00
 */
class TtsVendorFeignRepositoryImplTest {

    /**
     * JSON 处理器。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 普通文本常量。
     */
    private static final String PLAIN_TEXT = "欢迎办理业务";

    /**
     * 带 nameTag 的原始文本。
     */
    private static final String TAGGED_RAW_TEXT =
        "<speak>欢迎<say-as interpret-as=\"name\">张三</say-as>办理业务</speak>";

    /**
     * 验证小模型仍然使用 finalText。
     */
    /**
     * 使用错误格式 name 标签开始标签的原始文本。
     */
    private static final String COMPATIBLE_TAGGED_RAW_TEXT =
        "<speak>娆㈣繋<say-as interpret-as=name>寮犱笁</say-as>鍔炵悊涓氬姟</speak>";

    @Test
    void testSynthesizeShouldUseFinalTextWhenEndpointIsSmall() {
        TtsVendorFeignRepositoryImpl repository = createRepository();
        TtsVendorAuthRepository authRepository = getAuthRepository(repository);
        ThsTtsSmallFeignClient smallFeignClient = getSmallFeignClient(repository);
        TtsPreparedTextEntity preparedTextEntity = TtsPreparedTextEntity.builder()
            .rawText(PLAIN_TEXT)
            .finalText("<speak>" + PLAIN_TEXT + "</speak>")
            .build();
        Mockito.when(authRepository.buildSmallModelForm("<speak>" + PLAIN_TEXT + "</speak>"))
            .thenReturn(Map.of(
                TtsVendorConstant.FORM_KEY_PARAM, "param",
                TtsVendorConstant.FORM_KEY_TS, "ts",
                TtsVendorConstant.FORM_KEY_SECRET_KEY, "secret"));
        Mockito.when(smallFeignClient.synthesize("param", "ts", "secret"))
            .thenReturn(ThsSmallTtsRespDTO.builder()
                .code(TtsVendorConstant.SMALL_MODEL_SUCCESS_CODE)
                .data(ThsSmallTtsRespDTO.DataPayload.builder().voiceData("base64-audio").logId("vendor-log").build())
                .build());

        TtsVendorResponseEntity responseEntity =
            repository.synthesize(TtsEndpointEnum.SMALL, "hithink", preparedTextEntity, "trace-1");

        Assertions.assertEquals("base64-audio", responseEntity.getBase64Audio());
        Assertions.assertEquals("<speak>" + PLAIN_TEXT + "</speak>", responseEntity.getSynthesizeText());
        Mockito.verify(authRepository).buildSmallModelForm("<speak>" + PLAIN_TEXT + "</speak>");
    }

    /**
     * 验证小模型业务失败时抛出专用错误码。
     */
    @Test
    void testSynthesizeShouldThrowSmallModelBizExceptionWhenSmallModelReturnsBusinessFailure() {
        TtsVendorFeignRepositoryImpl repository = createRepository();
        TtsVendorAuthRepository authRepository = getAuthRepository(repository);
        ThsTtsSmallFeignClient smallFeignClient = getSmallFeignClient(repository);
        TtsPreparedTextEntity preparedTextEntity = TtsPreparedTextEntity.builder()
            .rawText(PLAIN_TEXT)
            .finalText("<speak>" + PLAIN_TEXT + "</speak>")
            .build();
        Mockito.when(authRepository.buildSmallModelForm("<speak>" + PLAIN_TEXT + "</speak>"))
            .thenReturn(Map.of(
                TtsVendorConstant.FORM_KEY_PARAM, "param",
                TtsVendorConstant.FORM_KEY_TS, "ts",
                TtsVendorConstant.FORM_KEY_SECRET_KEY, "secret"));
        Mockito.when(smallFeignClient.synthesize("param", "ts", "secret"))
            .thenReturn(ThsSmallTtsRespDTO.builder()
                .code(50001)
                .note("small model failed")
                .build());

        BizException bizException = Assertions.assertThrows(
            BizException.class,
            () -> repository.synthesize(TtsEndpointEnum.SMALL, "hithink", preparedTextEntity, "trace-1"));

        Assertions.assertEquals(ErrorCodeEnum.TTS_SMALL_MODEL_CALL_FAILED.getCode(), bizException.getErrorCode());
        Assertions.assertEquals(ErrorCodeEnum.TTS_SMALL_MODEL_CALL_FAILED.getMessage(), bizException.getErrorMessage());
    }

    /**
     * 验证大模型会改用 rawText，并在必要时移除 nameTag。
     */
    @Test
    void testSynthesizeShouldUseRawTextWithoutNameTagWhenEndpointIsLarge() {
        TtsVendorFeignRepositoryImpl repository = createRepository();
        TtsVendorAuthRepository authRepository = getAuthRepository(repository);
        ThsTtsLargeFeignClient largeFeignClient = getLargeFeignClient(repository);
        TtsPreparedTextEntity preparedTextEntity = TtsPreparedTextEntity.builder()
            .rawText(TAGGED_RAW_TEXT)
            .finalText("<speak>欢迎<say-as interpret-as=\"name\">张三</say-as>办理业务</speak>")
            .build();
        Mockito.when(authRepository.buildLargeModelAuthorization(ArgumentMatchers.anyString())).thenReturn("auth-value");
        Mockito.when(largeFeignClient.synthesize(ArgumentMatchers.eq("auth-value"), ArgumentMatchers.any(ThsLargeTtsReqDTO.class)))
            .thenReturn(ThsLargeTtsRespDTO.builder()
                .code(TtsVendorConstant.LARGE_MODEL_SUCCESS_CODE)
                .traceId("vendor-trace")
                .data(ThsLargeTtsRespDTO.DataPayload.builder().audio("large-audio").build())
                .build());
        ArgumentCaptor<ThsLargeTtsReqDTO> requestCaptor = ArgumentCaptor.forClass(ThsLargeTtsReqDTO.class);

        TtsVendorResponseEntity responseEntity =
            repository.synthesize(TtsEndpointEnum.LARGE, "hithink", preparedTextEntity, "trace-1");

        Mockito.verify(largeFeignClient).synthesize(ArgumentMatchers.eq("auth-value"), requestCaptor.capture());
        Assertions.assertEquals("欢迎张三办理业务", requestCaptor.getValue().getText());
        Assertions.assertEquals("large-audio", responseEntity.getBase64Audio());
        Assertions.assertEquals("欢迎张三办理业务", responseEntity.getSynthesizeText());
    }

    /**
     * 验证大模型响应包含额外字段时仍可正常解析。
     *
     * @throws Exception JSON 解析异常
     */
    @Test
    void testSynthesizeShouldRemoveCompatibleNameTagWhenEndpointIsLarge() {
        TtsVendorFeignRepositoryImpl repository = createRepository();
        TtsVendorAuthRepository authRepository = getAuthRepository(repository);
        ThsTtsLargeFeignClient largeFeignClient = getLargeFeignClient(repository);
        TtsPreparedTextEntity preparedTextEntity = TtsPreparedTextEntity.builder()
            .rawText(COMPATIBLE_TAGGED_RAW_TEXT)
            .finalText(TAGGED_RAW_TEXT)
            .build();
        Mockito.when(authRepository.buildLargeModelAuthorization(ArgumentMatchers.anyString())).thenReturn("auth-value");
        Mockito.when(largeFeignClient.synthesize(ArgumentMatchers.eq("auth-value"), ArgumentMatchers.any(ThsLargeTtsReqDTO.class)))
            .thenReturn(ThsLargeTtsRespDTO.builder()
                .code(TtsVendorConstant.LARGE_MODEL_SUCCESS_CODE)
                .traceId("vendor-trace")
                .data(ThsLargeTtsRespDTO.DataPayload.builder().audio("large-audio").build())
                .build());
        ArgumentCaptor<ThsLargeTtsReqDTO> requestCaptor = ArgumentCaptor.forClass(ThsLargeTtsReqDTO.class);

        repository.synthesize(TtsEndpointEnum.LARGE, "hithink", preparedTextEntity, "trace-1");

        Mockito.verify(largeFeignClient).synthesize(ArgumentMatchers.eq("auth-value"), requestCaptor.capture());
        Assertions.assertEquals("娆㈣繋寮犱笁鍔炵悊涓氬姟", requestCaptor.getValue().getText());
    }

    @Test
    void testLargeModelResponseDtoShouldIgnoreUnknownFields() throws Exception {
        String responseJson = "{\"code\":10000,\"errorName\":\"SUCCESS\",\"msg\":\"ok\",\"traceId\":\"trace-1\","
            + "\"data\":{\"audio\":\"base64-audio\",\"duration\":1.2}}";

        ThsLargeTtsRespDTO responseDTO = OBJECT_MAPPER.readValue(responseJson, ThsLargeTtsRespDTO.class);

        Assertions.assertEquals(TtsVendorConstant.LARGE_MODEL_SUCCESS_CODE, responseDTO.getCode());
        Assertions.assertEquals("base64-audio", responseDTO.getData().getAudio());
    }

    /**
     * 验证大模型业务失败时抛出专用错误码。
     */
    @Test
    void testSynthesizeShouldThrowLargeModelBizExceptionWhenLargeModelReturnsBusinessFailure() {
        TtsVendorFeignRepositoryImpl repository = createRepository();
        TtsVendorAuthRepository authRepository = getAuthRepository(repository);
        ThsTtsLargeFeignClient largeFeignClient = getLargeFeignClient(repository);
        TtsPreparedTextEntity preparedTextEntity = TtsPreparedTextEntity.builder()
            .rawText(PLAIN_TEXT)
            .finalText("<speak>" + PLAIN_TEXT + "</speak>")
            .build();
        Mockito.when(authRepository.buildLargeModelAuthorization(ArgumentMatchers.anyString())).thenReturn("auth-value");
        Mockito.when(largeFeignClient.synthesize(ArgumentMatchers.eq("auth-value"), ArgumentMatchers.any(ThsLargeTtsReqDTO.class)))
            .thenReturn(ThsLargeTtsRespDTO.builder()
                .code(40001)
                .errorType("INVALID_PARAMETER")
                .msg("voiceId invalid")
                .traceId("vendor-trace")
                .build());

        BizException bizException = Assertions.assertThrows(
            BizException.class,
            () -> repository.synthesize(TtsEndpointEnum.LARGE, "qwen", preparedTextEntity, "trace-1"));

        Assertions.assertEquals(ErrorCodeEnum.TTS_LARGE_MODEL_CALL_FAILED.getCode(), bizException.getErrorCode());
        Assertions.assertEquals(ErrorCodeEnum.TTS_LARGE_MODEL_CALL_FAILED.getMessage(), bizException.getErrorMessage());
    }

    /**
     * 创建厂商仓储。
     *
     * @return 厂商仓储
     */
    private TtsVendorFeignRepositoryImpl createRepository() {
        TtsVendorFeignRepositoryImpl repository = new TtsVendorFeignRepositoryImpl();
        ReflectionTestUtils.setField(repository, "thsTtsSmallFeignClient", Mockito.mock(ThsTtsSmallFeignClient.class));
        ReflectionTestUtils.setField(repository, "thsTtsLargeFeignClient", Mockito.mock(ThsTtsLargeFeignClient.class));
        ReflectionTestUtils.setField(repository, "ttsVendorAuthRepository", Mockito.mock(TtsVendorAuthRepository.class));
        ReflectionTestUtils.setField(repository, "speechTtsProperties", buildSpeechTtsProperties());
        return repository;
    }

    /**
     * 构建 TTS 配置。
     *
     * @return TTS 配置
     */
    private SpeechTtsProperties buildSpeechTtsProperties() {
        SpeechTtsProperties speechTtsProperties = new SpeechTtsProperties();
        SpeechTtsProperties.EndpointConfig endpointConfig = new SpeechTtsProperties.EndpointConfig();
        endpointConfig.setLargeModelVoiceId("large-voice-id");
        speechTtsProperties.setEndpoint(endpointConfig);

        SpeechTtsProperties.SsmlConfig ssmlConfig = new SpeechTtsProperties.SsmlConfig();
        ssmlConfig.setSpeakStartTag("<speak>");
        ssmlConfig.setSpeakEndTag("</speak>");
        speechTtsProperties.setSsml(ssmlConfig);

        SpeechTtsProperties.NameTagConfig nameTagConfig = new SpeechTtsProperties.NameTagConfig();
        nameTagConfig.setNameTagStartTag("<say-as interpret-as=\"name\">");
        nameTagConfig.setNameTagEndTag("</say-as>");
        nameTagConfig.setCompatibleNameTagStartTagList(List.of(
            "<say-as interpret-as='name'>",
            "<say-as interpret-as=’name‘>",
            "<say-as interpret-as=‘name’>",
            "<say-as interpret-as=name>"));
        speechTtsProperties.setNameTag(nameTagConfig);
        return speechTtsProperties;
    }

    /**
     * 获取小模型 Feign 客户端。
     *
     * @param repository 厂商仓储
     * @return 小模型 Feign 客户端
     */
    private ThsTtsSmallFeignClient getSmallFeignClient(TtsVendorFeignRepositoryImpl repository) {
        return (ThsTtsSmallFeignClient) ReflectionTestUtils.getField(repository, "thsTtsSmallFeignClient");
    }

    /**
     * 获取大模型 Feign 客户端。
     *
     * @param repository 厂商仓储
     * @return 大模型 Feign 客户端
     */
    private ThsTtsLargeFeignClient getLargeFeignClient(TtsVendorFeignRepositoryImpl repository) {
        return (ThsTtsLargeFeignClient) ReflectionTestUtils.getField(repository, "thsTtsLargeFeignClient");
    }

    /**
     * 获取鉴权仓储。
     *
     * @param repository 厂商仓储
     * @return 鉴权仓储
     */
    private TtsVendorAuthRepository getAuthRepository(TtsVendorFeignRepositoryImpl repository) {
        return (TtsVendorAuthRepository) ReflectionTestUtils.getField(repository, "ttsVendorAuthRepository");
    }
}
