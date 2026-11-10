package com.rbac.rbac0.mapper;

import com.rbac.rbac0.entity.BusinessApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationMapper extends JpaRepository<BusinessApplication, Long> {}
