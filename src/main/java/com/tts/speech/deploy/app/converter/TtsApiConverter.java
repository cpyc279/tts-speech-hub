package com.tts.speech.deploy.app.converter;

import com.tts.speech.deploy.modules.tts.domain.entity.TtsAudioEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsRequestEntity;
import com.tts.speech.deploy.app.dto.TtsAudioDTO;
import com.tts.speech.deploy.interfaces.rest.req.TtsGenerateReq;
import org.mapstruct.Mapper;

/**
 * TTS 接口转换器。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Mapper(componentModel = "spring")
public interface TtsApiConverter {

    /**
     * 转换请求对象。
     *
     * @param request 请求对象
     * @return 领域请求
     */
    TtsRequestEntity toEntity(TtsGenerateReq request);

    /**
     * 转换音频对象。
     *
     * @param audioEntity 领域音频
     * @return 响应 DTO
     */
    TtsAudioDTO toAudioDTO(TtsAudioEntity audioEntity);
}
