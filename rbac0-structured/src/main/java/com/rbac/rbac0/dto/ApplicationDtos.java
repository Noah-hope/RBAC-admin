package com.rbac.rbac0.dto;

import static com.rbac.rbac0.dto.DepartmentDtos.MAX_ID;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.hibernate.validator.constraints.CodePointLength;
import org.hibernate.validator.constraints.UniqueElements;

public final class ApplicationDtos {

  public static final String PERMISSION_CODE = "(?U)[^:\\s]+:[^:\\s]+";

  private ApplicationDtos() {}

  public record ApplicationInput(@NotEmpty @CodePointLength(max = 24) String name) {}

  public record ApplicationCreate(
      @NotEmpty @CodePointLength(max = 24) String name,
      @Pattern(regexp = "[a-z][a-z0-9_-]*") @CodePointLength(max = 64) String code) {}

  public record ApplicationView(long id, String name, String code) {}

  public record ApplicationPage(List<ApplicationView> items, int page, int pageSize, long total) {}

  public record PermissionCreate(
      @NotEmpty @CodePointLength(max = 24) String name,
      @NotNull @CodePointLength(max = 64) @Pattern(regexp = PERMISSION_CODE) String code,
      @NotNull @Pattern(regexp = "MENU|BUTTON") String type,
      @Positive @Max(MAX_ID) Long parentId,
      @UniqueElements List<@NotNull @Positive Long> apiIds) {}

  public static final class PermissionUpdate {
    @JsonSetter(nulls = Nulls.FAIL)
    @UniqueElements
    public List<@NotNull @Positive Long> apiIds;

    @JsonSetter(nulls = Nulls.FAIL)
    @CodePointLength(min = 1, max = 24)
    public String name;

    @JsonSetter(nulls = Nulls.FAIL)
    @CodePointLength(max = 64)
    @Pattern(regexp = PERMISSION_CODE)
    public String code;
  }

  public record PermissionView(
      long id,
      long applicationId,
      String name,
      String code,
      String type,
      Long parentId,
      List<ApiDtos.ApiView> apis) {}

  public record PermissionRef(long id, String name, String code) {}

  public record MenuNode(long id, String name, String code, List<PermissionRef> buttons) {}

  public record ApplicationPermissions(
      long applicationId, String applicationName, List<MenuNode> menus) {}
}
