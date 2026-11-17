package com.rbac.rbac1ddd.interfaces.rest.dto;

import static com.rbac.rbac1ddd.interfaces.rest.Ids.MAX_ID;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.CodePointLength;

/**
 * 修改部门的请求体（DepartmentUpdate），只修改出现的字段。
 *
 * <p>名称显式传 null 视为请求不合法；上级部门显式传 null 表示改为顶级部门，因此记录 parentId 是否出现。
 * 两项都不填时由应用层按业务规则拒绝，提示与 rbac1-structured 相同。
 */
@Getter
@NoArgsConstructor
public class DepartmentUpdateDTO {

  /** 新名称，1～24 个字符。 */
  @Setter
  @JsonSetter(nulls = Nulls.FAIL)
  @CodePointLength(min = 1, max = 24)
  private String name;

  /** 新的上级部门 ID，null 表示改为顶级部门。 */
  @Positive
  @Max(MAX_ID)
  private Long parentId;

  /** 请求中是否出现了 parentId。 */
  private boolean parentProvided;

  /**
   * Jackson 只在请求中出现 parentId 时调用，借此区分"未填写"和"改为顶级部门"。
   *
   * @param parentId 新的上级部门 ID，可以为 null
   */
  @JsonSetter("parentId")
  public void setParentId(Long parentId) {
    this.parentId = parentId;
    this.parentProvided = true;
  }
}
