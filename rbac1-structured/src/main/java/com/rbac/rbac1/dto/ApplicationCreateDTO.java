package com.rbac.rbac1.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.rbac.rbac1.entity.BusinessApplication;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.CodePointLength;

/** 登记应用的请求体；名称和应用标识与 {@link BusinessApplication} 同名，由 CloneFactory 拷贝。 */
@CopyTo(BusinessApplication.class)
@Getter
@Setter
@NoArgsConstructor
public class ApplicationCreateDTO {

  /** 应用名称，必填且不超过 24 个字符。 */
  @NotEmpty
  @CodePointLength(max = 24)
  private String name;

  /** 应用标识；不提交时由服务端生成，因此不参与拷贝。 */
  @Pattern(regexp = "[a-z][a-z0-9_-]*")
  @CodePointLength(max = 64)
  @CopyTo.Exclude(BusinessApplication.class)
  private String code;
}
