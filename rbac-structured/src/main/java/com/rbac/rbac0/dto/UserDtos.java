package com.rbac.rbac0.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.CodePointLength;

import static com.rbac.rbac0.dto.DepartmentDtos.MAX_ID;

public final class UserDtos {

    private UserDtos() {}

    public static final class UserCreate {
        @NotEmpty @CodePointLength(max = 32)
        public String username;

        @NotEmpty @CodePointLength(max = 24)
        public String name;

        @NotNull @Positive @Max(MAX_ID)
        public Long departmentId;

        @JsonSetter(nulls = Nulls.FAIL)
        @Pattern(regexp = "ACTIVE|DISABLED")
        public String status = "ACTIVE";

        @JsonSetter(nulls = Nulls.FAIL)
        public Boolean departmentAdministrator;
    }

    public static final class UserUpdate {
        @JsonSetter(nulls = Nulls.FAIL)
        @CodePointLength(min = 1, max = 32)
        public String username;

        @JsonSetter(nulls = Nulls.FAIL)
        @CodePointLength(min = 1, max = 24)
        public String name;

        @JsonSetter(nulls = Nulls.FAIL)
        @Positive @Max(MAX_ID)
        public Long departmentId;

        @JsonSetter(nulls = Nulls.FAIL)
        @Pattern(regexp = "ACTIVE|DISABLED")
        public String status;

        @JsonSetter(nulls = Nulls.FAIL)
        public Boolean departmentAdministrator;
    }

    public record DepartmentRef(long id, String name) {}

    public record RoleRef(long id, String name) {}

    public record UserView(long id, String username, String name, DepartmentRef department, String status,
                           boolean departmentAdministrator, List<RoleRef> businessRoles) {}

    public record UserCreated(UserView user, String initialPassword) {}

    public record UserPage(List<UserView> items, int page, int pageSize, long total) {}
}
