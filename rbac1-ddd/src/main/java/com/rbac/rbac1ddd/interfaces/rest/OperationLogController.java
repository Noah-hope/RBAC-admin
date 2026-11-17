package com.rbac.rbac1ddd.interfaces.rest;

import static com.rbac.rbac1ddd.interfaces.rest.SessionInterceptor.OPERATOR;

import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.application.audit.OperationLogQueryService;
import com.rbac.rbac1ddd.interfaces.rest.vo.OperationLogVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.PageVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.CodePointLength;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 操作记录接口：按关键字分页查看操作者可见的操作记录。 */
@RestController
@RequiredArgsConstructor
public class OperationLogController {

  private final OperationLogQueryService logs;

  /** 分页搜索操作记录，关键字匹配操作人、可见对象或操作内容。 */
  @GetMapping("/api/v1/operation-logs")
  public PageVO<OperationLogVO> list(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @RequestParam(defaultValue = "1")
      @Positive
      int page,
      @RequestParam(defaultValue = "20")
      @Positive
      @Max(100)
      int pageSize,
      @RequestParam(defaultValue = "")
      @CodePointLength(max = 100)
      String keyword) {
    return PageVO.from(logs.list(operator, keyword, page, pageSize), OperationLogVO::from);
  }
}
