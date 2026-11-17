package com.rbac.rbac1ddd.infrastructure.persistence.dao;

import com.rbac.rbac1ddd.infrastructure.persistence.po.DepartmentPo;
import org.springframework.data.jpa.repository.JpaRepository;

/** sys_department 表的 Spring Data 访问接口。 */
public interface DepartmentDao extends JpaRepository<DepartmentPo, Long> {}
