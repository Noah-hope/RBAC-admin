package com.rbac.rbac0.mapper;

import com.rbac.rbac0.entity.LoginSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SessionMapper extends JpaRepository<LoginSession, byte[]> {

    @Modifying
    @Query("delete from LoginSession session where session.tokenHash = :tokenHash")
    int revoke(byte[] tokenHash);
}
