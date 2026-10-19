package com.adgn.backend.scope.service;

import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.identity.service.CurrentIdentityReader;
import com.adgn.backend.people.OrganizationReader;
import com.adgn.backend.scope.repository.*;
import org.springframework.security.oauth2.jwt.Jwt;
import javax.sql.DataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;

public final class ManagementContextResolver {
    public record Selection(String appointmentId,String appointmentVersion,String scopeVersion) { }
    private final DataSource source;
    private final CurrentIdentityReader identity;
    private final ScopeRepository scopes;
    private final AppointmentRepository appointments;
    private final OrganizationReader people;
    public ManagementContextResolver(DataSource source,CurrentIdentityReader identity,ScopeRepository scopes,
            AppointmentRepository appointments,OrganizationReader people) {
        this.source=source;this.identity=identity;this.scopes=scopes;this.appointments=appointments;this.people=people;
    }
    /** No new transaction: called after the caller establishes its RR snapshot or guarded RC boundary. */
    public ManagementContext resolve(Jwt token,Selection selection){
        if(!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source))
            throw new IllegalStateException("Management context requires the caller transaction on its own datasource");
        var account=identity.admin(token,false).account();
        var s=selection==null?new Selection(null,null,null):selection;
        boolean absent=s.appointmentId()==null && s.appointmentVersion()==null && s.scopeVersion()==null;
        if(account.superAdmin() && absent)return new ManagementContext(account.id(),null,null,null,null,null,null,Set.of());
        ScopeInput.identifier(s.appointmentId());long av=ScopeInput.version(s.appointmentVersion()),sv=ScopeInput.version(s.scopeVersion());
        var appointment=appointments.selected(s.appointmentId()).orElseThrow(ManagementContextResolver::changed);
        if(!account.id().equals(appointment.getAccountId()))throw forbidden();
        if(!"ACTIVE".equals(appointment.getStatus()) || av!=appointment.getVersion() || sv!=appointment.getScopeVersion())throw changed();
        if(account.employeeId()==null)throw forbidden();
        var company=UUID.fromString(appointment.getCompanyId());var department=UUID.fromString(appointment.getDepartmentId());
        var employee=UUID.fromString(account.employeeId());
        if(people.company(company).isEmpty() || people.department(department).filter(d->d.companyId().equals(company)).isEmpty()
                || people.employee(employee).filter(e->"ACTIVE".equals(e.status())).isEmpty()
                || people.applicableEmployments(employee,company,department).isEmpty())throw forbidden();
        return new ManagementContext(account.id(),appointment.getId(),av,appointment.getScopeId(),sv,company.toString(),department.toString(),new HashSet<>(scopes.systems(appointment.getScopeId())));
    }
    private static IdentityFailure changed(){return new IdentityFailure(409,"CONTEXT_CHANGED");}
    private static IdentityFailure forbidden(){return new IdentityFailure(403,"FORBIDDEN");}
}
