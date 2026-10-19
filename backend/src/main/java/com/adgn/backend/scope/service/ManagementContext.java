package com.adgn.backend.scope.service;

import java.util.Set;

/** Server-derived current facts, valid only within the caller transaction; never accept this from JSON. */
public record ManagementContext(String accountId,String appointmentId,Long appointmentVersion,String scopeId,
        Long scopeVersion,String companyId,String departmentId,Set<String> systemIds) {
    public ManagementContext { systemIds=Set.copyOf(systemIds); }
    public boolean global(){return appointmentId==null;}
}
