package com.rbac.rbac0.mapper;

import java.util.Optional;

import com.rbac.rbac0.entity.BusinessApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ApplicationMapper extends JpaRepository<BusinessApplication, Long> {

    @Query("select app.id from BusinessApplication app where app.tokenHash = :tokenHash")
    Optional<Long> findIdByTokenHash(byte[] tokenHash);
}
