package com.tts.speech.deploy.app.converter;

import com.tts.speech.deploy.modules.tts.domain.entity.TtsAudioEntity;
import com.tts.speech.deploy.modules.tts.domain.entity.TtsResponseEntity;
import com.tts.speech.deploy.app.dto.TtsAudioDTO;
import com.tts.speech.deploy.app.dto.TtsGenerateDataDTO;
import org.mapstruct.Mapper;

/**
 * TTS 响应组装器。
 *
 * @author yangchen5
 * @since 2026-03-06 17:45:46
 */
@Mapper(componentModel = "spring")
public interface TtsResponseConverter {

    /**
     * 转换 TTS 响应数据。
     *
     * @param responseEntity 领域响应
     * @return 响应 DTO
     */
    TtsGenerateDataDTO toDataDTO(TtsResponseEntity responseEntity);

    /**
     * 转换音频数据。
     *
     * @param audioEntity 领域音频
     * @return 音频 DTO
     */
    TtsAudioDTO toAudioDTO(TtsAudioEntity audioEntity);
}
