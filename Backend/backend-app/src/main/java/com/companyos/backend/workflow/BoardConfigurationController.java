package com.companyos.backend.workflow;

import com.companyos.tenantcontext.TenantContextHolder;
import com.companyos.workflow.BoardConfiguration;
import com.companyos.workflow.BoardConfigurationCommand;
import com.companyos.workflow.BoardConfigurationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/board-config")
public class BoardConfigurationController {
    private final BoardConfigurationService service;

    public BoardConfigurationController(BoardConfigurationService service) { this.service = service; }

    @GetMapping
    public BoardConfiguration get(@PathVariable UUID projectId) {
        return service.get(TenantContextHolder.required().tenantId(), projectId);
    }

    @PutMapping
    public BoardConfiguration update(@PathVariable UUID projectId, @Valid @RequestBody BoardRequest request) {
        var context = TenantContextHolder.required();
        return service.update(context.tenantId(), context.userId(), projectId,
                new BoardConfigurationCommand(request.grouping(), request.swimlane(), request.cardFields(), request.wipLimits()));
    }

    public record BoardRequest(
            @NotBlank @Size(max = 32) String grouping,
            @NotBlank @Size(max = 32) String swimlane,
            @Size(max = 10) List<@NotBlank @Size(max = 32) String> cardFields,
            @Size(max = 24) Map<@NotBlank @Size(max = 32) String, @Min(0) @Max(10000) Integer> wipLimits) { }
}
