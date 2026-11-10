package com.rbac.rbac0.mapper;

import java.util.Optional;

import com.rbac.rbac0.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMapper extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);
}
