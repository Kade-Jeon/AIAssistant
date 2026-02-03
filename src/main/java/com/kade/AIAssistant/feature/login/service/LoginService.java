package com.kade.AIAssistant.feature.login.service;

import com.kade.AIAssistant.common.exceptions.customs.InvalidRequestException;
import com.kade.AIAssistant.feature.login.dto.request.LoginRequest;
import com.kade.AIAssistant.feature.login.dto.response.LoginResponse;
import com.kade.AIAssistant.feature.preference.entity.UserEntity;
import com.kade.AIAssistant.feature.preference.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginService {

    private final UserRepository userRepository;

    public LoginResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmailIdAndPassword(
                request.emailId(),
                request.password()
        ).orElseThrow(() -> new InvalidRequestException("이메일 또는 비밀번호가 일치하지 않습니다."));

        return new LoginResponse(
                user.getId(),
                user.getEmailId(),
                user.getPlan()
        );
    }
}
