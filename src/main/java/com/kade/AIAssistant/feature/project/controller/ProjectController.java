package com.kade.AIAssistant.feature.project.controller;

import com.kade.AIAssistant.feature.project.dto.reqeust.CreateProjectRequest;
import com.kade.AIAssistant.feature.project.service.ProjectRagService;
import com.kade.AIAssistant.feature.project.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
    private final ProjectRagService projectRagService;

    @PostMapping("/create")
    public ResponseEntity<?> createProject(
            @RequestBody @Valid CreateProjectRequest request,
            @RequestHeader(value = "USER-ID") String userIdHeader,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(projectService.createProject(userIdHeader, request));
    }

    @PostMapping("/{projectId}/doc")
    public ResponseEntity<?> addDocument(
            @PathVariable(value = "projectId", required = true) String projectId,
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = "USER-ID") String userIdHeader,
            HttpServletRequest httpRequest
    ) {
        projectRagService.addDocument(userIdHeader, projectId, file);
        return ResponseEntity.ok().build();
    }
}
