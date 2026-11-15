package com.rbac.rbac1.service;

import java.util.List;
import java.util.Set;

public final class ApprovalRules {
  private ApprovalRules() {}

  public static List<Long> extraPermissions(
      Set<Long> selected, Set<Long> owned, long requested, Long parent) {
    return selected.stream()
        .filter(id -> id != requested && !id.equals(parent) && !owned.contains(id))
        .sorted()
        .toList();
  }
}
