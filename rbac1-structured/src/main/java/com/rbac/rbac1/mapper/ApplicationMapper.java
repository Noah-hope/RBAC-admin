package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.BusinessApplication;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationMapper extends JpaRepository<BusinessApplication, Long> {
  Optional<BusinessApplication> findByCode(String code);
}
