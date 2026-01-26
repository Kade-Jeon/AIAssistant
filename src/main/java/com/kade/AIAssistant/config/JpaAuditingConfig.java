package com.kade.AIAssistant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화.
 * <p>엔티티의 @CreatedDate, @LastModifiedDate를 자동으로 채운다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
