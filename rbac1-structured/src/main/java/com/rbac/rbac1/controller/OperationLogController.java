package com.rbac.rbac1.controller;

import static com.rbac.rbac1.controller.SessionInterceptor.ACTOR;

import com.rbac.rbac1.service.OperationLogQueryService;
import com.rbac.rbac1.service.SessionUser;
import com.rbac.rbac1.vo.OperationLogVO;
import com.rbac.rbac1.vo.PageVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.CodePointLength;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 操作日志查询接口。 */
@RestController
@RequiredArgsConstructor
public class OperationLogController {

  private final OperationLogQueryService logs;

  /** 按操作者可见范围分页查询操作日志；关键字长度按 Unicode 码点计算。 */
  @GetMapping("/api/v1/operation-logs")
  public PageVO<OperationLogVO> list(
      @RequestAttribute(ACTOR)
      SessionUser actor,
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
    return logs.list(actor, page, pageSize, keyword);
  }
}
