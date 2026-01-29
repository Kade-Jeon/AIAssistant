package com.kade.AIAssistant.feature.preference.repository;

import com.kade.AIAssistant.feature.preference.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    
    Optional<UserEntity> findByEmailId(String emailId);
    
    Optional<UserEntity> findByEmailIdAndPassword(String emailId, String password);
}
