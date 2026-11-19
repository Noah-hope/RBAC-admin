package com.rbac.rbac1.vo;

/**
 * 业务应用的响应体。
 *
 * @param id 应用 ID
 * @param name 应用名称
 * @param code 应用标识
 */
public record ApplicationVO(long id, String name, String code) {}
