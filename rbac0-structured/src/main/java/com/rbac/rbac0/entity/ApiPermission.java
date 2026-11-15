package com.rbac.rbac0.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "sys_api_permission")
@IdClass(ApiPermission.Key.class)
public class ApiPermission {
    @Id
    public Long permissionId;

    @Id
    public Long apiId;

    public record Key(Long permissionId, Long apiId) implements Serializable {}
}
