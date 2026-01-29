package com.kade.AIAssistant.feature.preference.service;

import com.kade.AIAssistant.domain.reqeust.PreferenceRequest;
import com.kade.AIAssistant.domain.response.PreferenceResponse;
import com.kade.AIAssistant.feature.preference.entity.UserEntity;
import com.kade.AIAssistant.feature.preference.entity.UserPreferenceEntity;
import com.kade.AIAssistant.feature.preference.repository.UserPreferenceRepository;
import com.kade.AIAssistant.feature.preference.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreferenceService {

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;

    public PreferenceResponse getPreference(String userId) {
        UUID userUuid = UUID.fromString(userId);
        UserEntity user = userRepository.findById(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return userPreferenceRepository.findByUser(user)
                .map(pref -> new PreferenceResponse(
                        pref.getNickname() != null ? pref.getNickname() : "",
                        pref.getOccupation() != null ? pref.getOccupation() : "",
                        pref.getExtraInfo() != null ? pref.getExtraInfo() : ""
                ))
                .orElse(new PreferenceResponse("", "", ""));
    }

    @Transactional
    public PreferenceResponse updatePreference(String userId, PreferenceRequest request) {
        UUID userUuid = UUID.fromString(userId);
        UserEntity user = userRepository.findById(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        UserPreferenceEntity preference = userPreferenceRepository.findByUser(user)
                .orElseGet(() -> {
                    UserPreferenceEntity newPref = new UserPreferenceEntity(
                            user,
                            request.nickname(),
                            request.occupation(),
                            request.extraInfo()
                    );
                    return userPreferenceRepository.save(newPref);
                });

        // 기존 preference가 있으면 업데이트
        if (preference.getId() != null) {
            preference.update(request.nickname(), request.occupation(), request.extraInfo());
        }

        return new PreferenceResponse(
                preference.getNickname() != null ? preference.getNickname() : "",
                preference.getOccupation() != null ? preference.getOccupation() : "",
                preference.getExtraInfo() != null ? preference.getExtraInfo() : ""
        );
    }
}
