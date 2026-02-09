package com.kade.AIAssistant.feature.project.controller;

import com.kade.AIAssistant.feature.project.dto.reqeust.CreateProjectRequest;
import com.kade.AIAssistant.feature.project.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ai/proj")
@RequiredArgsConstructor
@Slf4j
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping("")
    public ResponseEntity<?> getProjectList(
            @RequestHeader(value = "USER-ID") String userIdHeader,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(projectService.getProjectList(userIdHeader));
    }

    @PostMapping("/create")
    public ResponseEntity<?> createProject(
            @RequestBody @Valid CreateProjectRequest request,
            @RequestHeader(value = "USER-ID") String userIdHeader,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(projectService.createProject(userIdHeader, request));
    }

    @PostMapping("/{conversationId}/doc")
    public ResponseEntity<?> addDocument(
            @PathVariable(value = "conversationId") String conversationId,
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = "USER-ID") String userIdHeader,
            HttpServletRequest httpRequest
    ) {
        projectService.addDocument(userIdHeader, conversationId, file);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{conversationId}/doc")
    public ResponseEntity<?> getDocumentList(
            @PathVariable(value = "conversationId") String conversationId,
            @RequestHeader(value = "USER-ID") String userIdHeader,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(projectService.getDocumentList(conversationId, userIdHeader));
    }

    @DeleteMapping("/{conversationId}/doc/{documentId}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable(value = "conversationId") String conversationId,
            @PathVariable(value = "documentId") String documentId,
            @RequestHeader(value = "USER-ID") String userIdHeader,
            HttpServletRequest httpRequest
    ) {
        projectService.deleteDocument(userIdHeader, conversationId, documentId);
        return ResponseEntity.noContent().build();
    }
}
