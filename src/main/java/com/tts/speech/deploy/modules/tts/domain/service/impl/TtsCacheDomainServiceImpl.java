package com.tts.speech.deploy.modules.tts.domain.service.impl;

import com.tts.speech.deploy.modules.tts.domain.repository.TtsCacheRepository;
import com.tts.speech.deploy.modules.tts.domain.service.TtsCacheDomainService;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * TTS 缓存领域服务实现。
 *
 * @author yangchen5
 * @since 2026-03-09 11:18:00
 */
@Service
public class TtsCacheDomainServiceImpl implements TtsCacheDomainService {

    @Resource
    private TtsCacheRepository ttsCacheRepository;

    /**
     * 过滤删除失败的缓存键。
     *
     * @param keyList 待删除缓存键列表
     * @return 删除失败的缓存键列表
     */
    @Override
    public List<String> filterDeleteFailedKeys(List<String> keyList) {
        List<String> failedKeyList = new ArrayList<>();
        for (String key : keyList) {
            if (!ttsCacheRepository.deleteKey(key)) {
                failedKeyList.add(key);
            }
        }
        return failedKeyList;
    }

    /**
     * 根据缓存 key 查询缓存 value。
     *
     * @param key 缓存 key
     * @return 缓存 value
     */
    @Override
    public Object getCacheValue(String key) {
        return ttsCacheRepository.getCacheValue(key);
    }
}
