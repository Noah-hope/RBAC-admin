package com.rbac.rbac0.dto;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.*;
import java.util.List;
public final class ApiDtos {
    private ApiDtos() {}
    public record ApiInput(@NotBlank @Size(max=128) String operationId,
                           @NotNull @Pattern(regexp="GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS") String httpMethod,
                           @NotNull @Size(max=255) @Pattern(regexp="^/[^?#\\s]*$") String path) {}
    public static class ApiUpdate {
        @JsonSetter(nulls=Nulls.FAIL) @Size(min=1,max=128) public String operationId;
        @JsonSetter(nulls=Nulls.FAIL) @Pattern(regexp="GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS") public String httpMethod;
        @JsonSetter(nulls=Nulls.FAIL) @Size(max=255) @Pattern(regexp="^/[^?#\\s]*$") public String path;
    }
    public record ApiView(long id,long applicationId,String operationId,String httpMethod,String path,List<Long> permissionIds) {}
    public record ApiPage(List<ApiView> items,int page,int pageSize,long total) {}
    public record PermissionSummary(long id,long applicationId,String name,String code,String type,Long parentId) {
        public static PermissionSummary of(com.rbac.rbac0.entity.Permission p) {
            return new PermissionSummary(p.id,p.applicationId,p.name,p.code,p.type,p.parentId);
        }
    }
}
