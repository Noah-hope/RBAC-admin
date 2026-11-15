package com.rbac.rbac1.dto;

import java.time.Instant;
import java.util.List;

public final class OperationLogDtos {

    private OperationLogDtos() {}

    public record OperationLogView(long id, Instant occurredAt, String actorName, String action,
                                   List<String> targetNames, String detail, String result) {}

    public record OperationLogPage(List<OperationLogView> items, int page, int pageSize, long total) {}
}
