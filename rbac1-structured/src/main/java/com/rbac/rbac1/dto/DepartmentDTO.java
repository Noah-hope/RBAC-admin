package com.rbac.rbac1.dto;

import static com.rbac.rbac1.dto.Formats.MAX_ID;

import cn.edu.xmu.clonefactory.CopyTo;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rbac.rbac1.entity.Department;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.CodePointLength;

/**
 * 新增部门的请求体；名称和上级与 {@link Department} 同名，由 CloneFactory 拷贝。
 *
 * <p>上级部门必须显式提交（顶层部门提交 null），因此用构造器绑定并声明 required，
 * 缺少该字段时返回请求不合法。
 */
@CopyTo(Department.class)
@Getter
@Setter
public class DepartmentDTO {

  /** 部门名称，必填且不超过 24 个字符。 */
  @NotEmpty
  @CodePointLength(max = 24)
  private String name;

  /** 上级部门 ID，顶层部门为 null。 */
  @Positive
  @Max(MAX_ID)
  private Long parentId;

  /**
   * JSON 绑定入口，要求请求中出现 parentId。
   *
   * @param name 部门名称
   * @param parentId 上级部门 ID，顶层部门为 null
   */
  @JsonCreator
  public DepartmentDTO(
      @JsonProperty("name")
      String name,
      @JsonProperty(value = "parentId", required = true)
      Long parentId) {
    this.name = name;
    this.parentId = parentId;
  }
}
