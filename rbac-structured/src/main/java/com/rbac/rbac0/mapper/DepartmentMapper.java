package com.rbac.rbac0.mapper;

import java.util.List;

import com.rbac.rbac0.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentMapper extends JpaRepository<Department, Long> {

    List<Department> findAllByOrderByIdAsc();
}
