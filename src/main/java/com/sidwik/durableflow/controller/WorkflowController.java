package com.sidwik.durableflow.controller;

import com.sidwik.durableflow.dto.request.StartWorkflowRequest;
import com.sidwik.durableflow.dto.response.WorkflowEventResponse;
import com.sidwik.durableflow.dto.response.WorkflowResponse;
import com.sidwik.durableflow.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {
    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowResponse start(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody StartWorkflowRequest request) {
        return WorkflowResponse.from(workflowService.start(userId(jwt), request.name()));
    }

    @GetMapping
    public List<WorkflowResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return workflowService.list(userId(jwt)).stream().map(WorkflowResponse::from).toList();
    }

    @GetMapping("/{workflowId}")
    public WorkflowResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workflowId) {
        return WorkflowResponse.from(workflowService.get(userId(jwt), workflowId));
    }

    @PostMapping("/{workflowId}/cancel")
    public WorkflowResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID workflowId) {
        return WorkflowResponse.from(workflowService.cancel(userId(jwt), workflowId));
    }

    @GetMapping("/{workflowId}/events")
    public List<WorkflowEventResponse> events(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable UUID workflowId) {
        return workflowService.events(userId(jwt), workflowId).stream()
                .map(WorkflowEventResponse::from).toList();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
