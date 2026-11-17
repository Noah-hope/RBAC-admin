package com.rbac.rbac1ddd.application.app;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.NOT_FOUND;

import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.application.shared.OperationRecorder;
import com.rbac.rbac1ddd.application.shared.OperationTarget;
import com.rbac.rbac1ddd.application.shared.PageResult;
import com.rbac.rbac1ddd.domain.app.BusinessApplication;
import com.rbac.rbac1ddd.domain.app.BusinessApplicationRepository;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.shared.DomainPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用服务：业务应用的查询、登记和改名，仅限总管理员。
 *
 * <p>只负责编排：检查操作者身份、读取应用、调用应用聚合的业务方法、保存、写全局操作记录。
 * 编码生成和改名规则在 {@link BusinessApplication}，名称和编码重复由仓储翻译。
 */
@Service
@RequiredArgsConstructor
public class BusinessApplicationService {

  private final BusinessApplicationRepository applications;
  private final OperationRecorder recorder;

  /** 按 ID 升序分页查询应用。 */
  @Transactional(readOnly = true)
  public PageResult<ApplicationResult> list(Operator operator, int page, int pageSize) {
    operator.requireTotalAdmin();
    DomainPage<BusinessApplication> found = applications.findPage(page, pageSize);
    return new PageResult<>(
        found.items().stream().map(ApplicationResult::of).toList(), page, pageSize, found.total());
  }

  /** 登记应用，未填写编码时由应用聚合生成，写入全局操作记录。 */
  @Transactional
  public ApplicationResult create(Operator operator, String name, String code) {
    operator.requireTotalAdmin();
    BusinessApplication application = BusinessApplication.register(name, code);
    applications.save(application);
    recorder.recordGlobal(
        operator,
        "新增应用",
        "登记应用「" + application.getName() + "」",
        OperationTarget.application(application));
    return ApplicationResult.of(application);
  }

  /** 修改应用名称，编码不变；名称有实际变化才写操作记录。 */
  @Transactional
  public ApplicationResult rename(Operator operator, long applicationId, String name) {
    operator.requireTotalAdmin();
    BusinessApplication application =
        applications
            .findById(applicationId)
            .orElseThrow(() -> new DomainException(NOT_FOUND, "应用不存在。"));
    String oldName = application.getName();
    if (application.rename(name)) {
      applications.save(application);
      recorder.recordGlobal(
          operator,
          "修改应用",
          "名称从「" + oldName + "」改为「" + name + "」",
          OperationTarget.application(application));
    }
    return ApplicationResult.of(application);
  }
}
