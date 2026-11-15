package com.rbac.rbac1.mapper;

import java.util.List;

import com.rbac.rbac1.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentMapper extends JpaRepository<Department, Long> {

    List<Department> findAllByOrderByIdAsc();
}
