package com.kade.AIAssistant.feature.preference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "user_preference")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true, updatable = false)
    private UserEntity user;

    @Column(name = "nickname", length = 30)
    private String nickname;

    @Column(name = "occupation", length = 30)
    private String occupation;

    @Column(name = "extra_info", length = 300)
    private String extraInfo;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserPreferenceEntity(UserEntity user, String nickname, String occupation, String extraInfo) {
        this.user = user;
        this.nickname = nickname;
        this.occupation = occupation;
        this.extraInfo = extraInfo;
    }

    public void update(String nickname, String occupation, String extraInfo) {
        this.nickname = nickname;
        this.occupation = occupation;
        this.extraInfo = extraInfo;
    }

}
