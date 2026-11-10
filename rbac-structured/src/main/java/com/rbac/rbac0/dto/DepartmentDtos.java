package com.rbac.rbac0.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.CodePointLength;

public final class DepartmentDtos {

    public static final long MAX_ID = 9_007_199_254_740_991L;

    private DepartmentDtos() {}

    public record DepartmentInput(
            @NotEmpty @CodePointLength(max = 24) String name,
            @JsonProperty(required = true) @Positive @Max(MAX_ID) Long parentId
    ) {}

    public static final class DepartmentUpdate {
        @JsonSetter(nulls = Nulls.FAIL)
        @CodePointLength(min = 1, max = 24)
        public String name;

        @Positive @Max(MAX_ID)
        private Long parentId;
        private boolean parentProvided;

        @JsonSetter("parentId")
        public void setParentId(Long parentId) {
            this.parentId = parentId;
            this.parentProvided = true;
        }

        public Long parentId() {
            return parentId;
        }

        public boolean parentProvided() {
            return parentProvided;
        }
    }

    public record DepartmentView(long id, String name, Long parentId) {}

    public record DepartmentNode(long id, String name, Long parentId, List<DepartmentNode> children) {}

    public record DepartmentTree(List<DepartmentNode> departments) {}
}
