package com.rbac.rbac1ddd.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.application.shared.OperationRecorder;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.user.ManagementRole;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一错误响应单元测试：不启动 Spring 容器，用独立的 MockMvc 和会抛出指定异常的测试接口，
 * 验证数据库不可用、未预料错误和未被仓储翻译的唯一约束冲突的响应与 rbac1-structured 相同。
 */
class RestExceptionHandlerTest {

  /** 抛出数据库连接失败。 */
  private static final String UNAVAILABLE = "/api/v1/test/unavailable";

  /** 抛出无法开启事务。 */
  private static final String NO_TRANSACTION = "/api/v1/test/no-transaction";

  /** 抛出未被仓储翻译的唯一约束冲突。 */
  private static final String DUPLICATE = "/api/v1/test/duplicate";

  /** 抛出唯一约束以外的完整性错误。 */
  private static final String INTEGRITY = "/api/v1/test/integrity";

  /** 抛出未预料的运行时错误。 */
  private static final String UNEXPECTED = "/api/v1/test/unexpected";

  /** 抛出用户名重复的领域异常。 */
  private static final String DOMAIN_DUPLICATE = "/api/v1/test/domain-duplicate";

  private final OperationRecorder recorder = mock(OperationRecorder.class);
  private final Operator operator = new Operator(1, "admin", "管理员", null, ManagementRole.TOTAL_ADMIN);
  private MockMvc mvc;

  /** 只注册测试接口和统一错误响应。 */
  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new RestExceptionHandler(recorder))
            .build();
  }

  @Test
  @DisplayName("RBAC0：数据库或事务不可用时返回 503 且不记录失败操作")
  void 数据库或事务不可用时返回503() throws Exception {
    for (String path : new String[] {UNAVAILABLE, NO_TRANSACTION}) {
      mvc.perform(asOperator(post(path)))
          .andExpect(status().isServiceUnavailable())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").value("urn:rbac-admin:problem:service-unavailable"))
          .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
          .andExpect(jsonPath("$.title").value("服务暂时不可用"))
          .andExpect(jsonPath("$.detail").value("暂时无法完成身份或权限检查。"));
    }
    verify(recorder, never()).recordFailure(any(), anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("RBAC0：未预料的错误返回 500，不暴露内部信息")
  void 未预料的错误返回500() throws Exception {
    mvc.perform(asOperator(post(UNEXPECTED)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.type").value("urn:rbac-admin:problem:internal-error"))
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.title").value("系统暂时不可用"))
        .andExpect(jsonPath("$.detail").value("请稍后重试。"));
    verify(recorder, never()).recordFailure(any(), anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("RBAC0：仓储未翻译的唯一约束冲突统一返回 409 并记录失败操作")
  void 未翻译的唯一约束冲突返回409() throws Exception {
    mvc.perform(asOperator(post(DUPLICATE)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:rbac-admin:problem:conflict"))
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(jsonPath("$.title").value("数据已存在"))
        .andExpect(jsonPath("$.detail").value("名称、应用标识、接口标识或路由已存在。"));
    verify(recorder).recordFailure(eq(operator), eq("POST"), anyString(), eq("CONFLICT"));
  }

  @Test
  @DisplayName("RBAC0：唯一约束以外的完整性错误返回 500，失败原因仍记为 CONFLICT")
  void 其他完整性错误返回500() throws Exception {
    mvc.perform(asOperator(post(INTEGRITY)))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    verify(recorder).recordFailure(eq(operator), eq("POST"), anyString(), eq("CONFLICT"));
  }

  @Test
  @DisplayName("RBAC0：数据重复的领域错误按 CONFLICT 记录失败原因，与结构化版本相同")
  void 数据重复的领域错误按CONFLICT记录失败原因() throws Exception {
    mvc.perform(asOperator(post(DOMAIN_DUPLICATE)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("该用户名已存在。"));
    verify(recorder).recordFailure(eq(operator), eq("POST"), anyString(), eq("CONFLICT"));
  }

  /** 模拟认证通过：把当前操作者放入请求属性，与 SessionInterceptor 的行为相同。 */
  private MockHttpServletRequestBuilder asOperator(MockHttpServletRequestBuilder request) {
    return request.requestAttr(SessionInterceptor.OPERATOR, operator);
  }

  /** 测试接口：每个路径抛出一种异常。 */
  @RestController
  static class FailingController {

    /** 数据库连接失败。 */
    @PostMapping(UNAVAILABLE)
    void unavailable() {
      throw new DataAccessResourceFailureException("connection refused");
    }

    /** 无法开启事务。 */
    @PostMapping(NO_TRANSACTION)
    void noTransaction() {
      throw new CannotCreateTransactionException("no connection");
    }

    /** MySQL 1062 唯一约束冲突，约束名不在已知列表中。 */
    @PostMapping(DUPLICATE)
    void duplicate() {
      throw new DataIntegrityViolationException(
          "duplicate", new SQLException("Duplicate entry for key 'uq_api_route'", "23000", 1062));
    }

    /** 外键约束冲突，不是唯一约束。 */
    @PostMapping(INTEGRITY)
    void integrity() {
      throw new DataIntegrityViolationException(
          "fk", new SQLException("foreign key violation", "23506", 1452));
    }

    /** 未预料的运行时错误。 */
    @PostMapping(UNEXPECTED)
    void unexpected() {
      throw new IllegalStateException("boom");
    }

    /** 仓储已翻译的用户名重复。 */
    @PostMapping(DOMAIN_DUPLICATE)
    void domainDuplicate() {
      throw new DomainException(DomainException.Reason.DUPLICATE, "该用户名已存在。");
    }
  }
}
