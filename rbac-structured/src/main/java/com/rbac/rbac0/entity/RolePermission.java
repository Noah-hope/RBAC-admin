package com.rbac.rbac0.entity;

import java.io.Serializable;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "sys_role_permission")
@IdClass(RolePermission.Key.class)
public class RolePermission {

    @Id
    public Long roleId;

    @Id
    public Long permissionId;

    public record Key(Long roleId, Long permissionId) implements Serializable {}
}
