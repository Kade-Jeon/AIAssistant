package com.kade.AIAssistant.feature.preference.controller;

import com.kade.AIAssistant.feature.preference.dto.reqeust.PreferenceRequest;
import com.kade.AIAssistant.feature.preference.service.PreferenceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/ai/pref")
public class PreferenceController {

    private final PreferenceService preferenceService;

    @GetMapping("")
    public ResponseEntity<?> getPreference(
            @RequestHeader(value = "USER-ID") String userIdHeader,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(preferenceService.getPreference(userIdHeader));
    }

    @PostMapping("")
    public ResponseEntity<?> updatePreference(
            @RequestHeader(value = "USER-ID") String userIdHeader,
            @RequestBody @Valid PreferenceRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(preferenceService.updatePreference(userIdHeader, request));
    }
}
