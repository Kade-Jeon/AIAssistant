package com.kade.AIAssistant.feature.preference.repository;

import com.kade.AIAssistant.feature.preference.entity.UserEntity;
import com.kade.AIAssistant.feature.preference.entity.UserPreferenceEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferenceRepository extends JpaRepository<UserPreferenceEntity, Long> {
    
    Optional<UserPreferenceEntity> findByUser(UserEntity user);
}
