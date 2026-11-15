package com.rbac.rbac0.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.CodePointLength;
import org.hibernate.validator.constraints.UniqueElements;

import static com.rbac.rbac0.dto.DepartmentDtos.MAX_ID;

public final class RoleDtos {

    private RoleDtos() {}

    public static final class RoleInput {
        @NotEmpty @CodePointLength(max = 24)
        public String name;

        @JsonSetter(nulls = Nulls.FAIL)
        @CodePointLength(max = 48)
        public String description = "";

        @NotNull @UniqueElements
        public List<@NotNull @Positive @Max(MAX_ID) Long> permissionIds;
    }

    public record RoleApplicationSummary(long applicationId, String applicationName, long permissionCount) {}

    public record RoleSummary(long id, String name, String description, List<RoleApplicationSummary> applications) {}

    public record RoleDetails(long id, String name, String description, List<RoleApplicationSummary> applications,
                              List<Long> permissionIds) {}

    public record RolePage(List<RoleSummary> items, int page, int pageSize, long total) {}
}
