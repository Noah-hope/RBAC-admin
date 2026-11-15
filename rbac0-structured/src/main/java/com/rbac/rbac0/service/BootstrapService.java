package com.rbac.rbac0.service;

import com.rbac.rbac0.entity.UserAccount;
import com.rbac.rbac0.entity.UserRole;
import com.rbac.rbac0.mapper.RoleMapper;
import com.rbac.rbac0.mapper.UserMapper;
import com.rbac.rbac0.mapper.UserRoleMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BootstrapService implements ApplicationRunner {

    private final RoleMapper roles;
    private final UserMapper users;
    private final UserRoleMapper userRoles;
    private final PasswordEncoder passwords;
    private final String username;
    private final String password;

    public BootstrapService(RoleMapper roles, UserMapper users, UserRoleMapper userRoles, PasswordEncoder passwords,
                            @Value("${rbac.bootstrap.username}") String username,
                            @Value("${rbac.bootstrap.password}") String password) {
        this.roles = roles;
        this.users = users;
        this.userRoles = userRoles;
        this.passwords = passwords;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var role = roles.findByRoleType("TOTAL_ADMIN");
        if (userRoles.existsByRoleId(role.id)) {
            return;
        }
        if (username.isBlank() || username.length() > 32 || password.isBlank()) {
            throw new IllegalStateException("Set RBAC_ADMIN_PASSWORD and a valid RBAC_ADMIN_USERNAME for first startup");
        }
        var admin = new UserAccount();
        admin.username = username;
        admin.name = "总管理员";
        admin.passwordHash = passwords.encode(password);
        admin.status = "ACTIVE";
        users.save(admin);

        var assignment = new UserRole();
        assignment.userId = admin.id;
        assignment.roleId = role.id;
        userRoles.save(assignment);
    }
}
