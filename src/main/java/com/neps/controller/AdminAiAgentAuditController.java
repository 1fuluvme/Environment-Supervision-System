package com.neps.controller;

import com.neps.dto.AiAgentAuditResponse;
import com.neps.service.AiAgentAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai/agent-audits")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理员Agent调用审计")
public class AdminAiAgentAuditController {

    private final AiAgentAuditService auditService;

    public AdminAiAgentAuditController(
            AiAgentAuditService auditService) {

        this.auditService = auditService;
    }

    @Operation(summary = "查询最近的Agent调用审计")
    @GetMapping
    public List<AiAgentAuditResponse> list(
            @RequestParam(required = false)
            String status) {

        return auditService.listForAdmin(
                status);
    }
}
