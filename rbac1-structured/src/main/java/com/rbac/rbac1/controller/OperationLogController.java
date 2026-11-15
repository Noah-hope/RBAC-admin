package com.rbac.rbac1.controller;

import com.rbac.rbac1.dto.OperationLogDtos.OperationLogPage;
import com.rbac.rbac1.dto.SessionDtos.SessionUser;
import com.rbac.rbac1.service.OperationLogQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.CodePointLength;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.rbac.rbac1.controller.SessionInterceptor.ACTOR;

@RestController
public class OperationLogController {

    private final OperationLogQueryService logs;

    public OperationLogController(OperationLogQueryService logs) {
        this.logs = logs;
    }

    @GetMapping("/api/v1/operation-logs")
    public OperationLogPage list(@RequestAttribute(ACTOR) SessionUser actor,
                                 @RequestParam(defaultValue = "1") @Positive int page,
                                 @RequestParam(defaultValue = "20") @Positive @Max(100) int pageSize,
                                 @RequestParam(defaultValue = "") @CodePointLength(max = 100) String keyword) {
        return logs.list(actor, page, pageSize, keyword);
    }
}
