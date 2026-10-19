package com.adgn.backend.scope.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Transactional(propagation=Propagation.MANDATORY,readOnly=true)
public interface AppointmentRepository extends Repository<AppointmentEntity,String> {
    interface View {
        String getId(); String getScopeId(); String getAccountId(); String getEmployeeId();
        String getStatus(); long getVersion(); long getScopeVersion(); String getCompanyId(); String getDepartmentId();
    }
    String FIELDS="SELECT ap.id AS id,ap.scope_id AS scopeId,ap.account_id AS accountId,a.employee_id AS employeeId,ap.status AS status,ap.appointment_version AS version,s.scope_version AS scopeVersion,s.company_id AS companyId,s.department_id AS departmentId ";
    String FROM=" FROM admin_appointment ap JOIN management_scope s ON s.id=ap.scope_id JOIN admin_account a ON a.id=ap.account_id ";
    String CURRENT=" WHERE ap.account_id=:account AND ap.status='ACTIVE' AND a.enabled=1 AND EXISTS(SELECT 1 FROM employee e WHERE e.id=a.employee_id AND e.status='ACTIVE') AND EXISTS(SELECT 1 FROM employment em WHERE em.employee_id=a.employee_id AND em.company_id=s.company_id AND em.department_id=s.department_id AND em.status='ACTIVE') ";
    @Query(value=FIELDS+FROM+CURRENT+" ORDER BY ap.id",nativeQuery=true)
    List<View> current(@Param("account")String account,Pageable page);
    @Query(value="SELECT COUNT(*)"+FROM+CURRENT,nativeQuery=true)
    long countCurrent(@Param("account")String account);
    @Query(value=FIELDS+FROM+" WHERE ap.id=:id",nativeQuery=true)
    Optional<View> selected(@Param("id")String id);
    @Query(value=FIELDS+FROM+" WHERE ap.scope_id=:scope AND (:status IS NULL OR ap.status=:status) ORDER BY ap.created_at DESC,ap.id DESC",nativeQuery=true)
    List<View> history(@Param("scope")String scope,@Param("status")String status,Pageable page);
    @Query(value="SELECT COUNT(*) FROM admin_appointment WHERE scope_id=:scope AND (:status IS NULL OR status=:status)",nativeQuery=true)
    long countHistory(@Param("scope")String scope,@Param("status")String status);
}
