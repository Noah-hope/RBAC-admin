package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.LoginSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SessionMapper extends JpaRepository<LoginSession, byte[]> {

    @Modifying
    @Query("delete from LoginSession session where session.tokenHash = :tokenHash")
    int revoke(byte[] tokenHash);

    @Modifying
    @Query("delete from LoginSession session where session.userId = :userId")
    void revokeAllForUser(long userId);
}
