package com.rbac.rbac0.mapper;
import java.util.Optional;
import com.rbac.rbac0.entity.BusinessApplication;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ApplicationMapper extends JpaRepository<BusinessApplication, Long> {
    Optional<BusinessApplication> findByCode(String code);
}
