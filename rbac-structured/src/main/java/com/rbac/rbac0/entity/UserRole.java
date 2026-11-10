package com.rbac.rbac0.entity;

import java.io.Serializable;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "sys_user_role")
@IdClass(UserRole.Key.class)
public class UserRole {

    @Id
    public Long userId;

    @Id
    public Long roleId;

    public record Key(Long userId, Long roleId) implements Serializable {}
}
