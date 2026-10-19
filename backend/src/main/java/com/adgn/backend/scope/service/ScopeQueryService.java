package com.adgn.backend.scope.service;

import com.adgn.backend.core.transaction.ReadBoundary;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.identity.service.CurrentIdentityReader;
import com.adgn.backend.scope.repository.*;
import com.adgn.backend.web.scope.dto.ScopeResponses.*;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.*;

public final class ScopeQueryService {
    private final ReadBoundary reads;
    private final CurrentIdentityReader identity;
    private final ScopeRepository scopes;
    private final AppointmentRepository appointments;
    public ScopeQueryService(ReadBoundary reads,CurrentIdentityReader identity,ScopeRepository scopes,AppointmentRepository appointments){this.reads=reads;this.identity=identity;this.scopes=scopes;this.appointments=appointments;}
    public Page<Appointment> mine(Jwt token,String page,String pageSize)throws Exception{
        var w=ScopeInput.page(page,pageSize);
        return reads.read(()->identity.admin(token,false),a->{
            long total=appointments.countCurrent(a.account().id());
            var items=w.offset()>=total?List.<Appointment>of():appointments.current(a.account().id(),w.pageable()).stream().map(this::appointment).toList();
            return new Page<>(items,w.page(),w.size(),total);
        });
    }
    public Page<Scope> list(Jwt token,String page,String pageSize,String company,String department)throws Exception{
        var w=ScopeInput.page(page,pageSize);if(company!=null)ScopeInput.identifier(company);if(department!=null)ScopeInput.identifier(department);
        return reads.read(()->global(token,false),a->{
            long total=scopes.count(company,department);
            var items=w.offset()>=total?List.<Scope>of():scopes.list(company,department,w.pageable()).stream().map(this::scope).toList();
            return new Page<>(items,w.page(),w.size(),total);
        });
    }
    public Scope get(Jwt token,String id)throws Exception{
        ScopeInput.identifier(id);
        return reads.read(()->global(token,true),a->scope(scopes.findById(id).orElseThrow(ScopeQueryService::missing)));
    }
    public Page<Appointment> history(Jwt token,String id,String page,String pageSize,String status)throws Exception{
        ScopeInput.identifier(id);var w=ScopeInput.page(page,pageSize);
        if(status!=null && !Set.of("ACTIVE","REVOKED","INVALIDATED").contains(status))throw IdentityFailure.invalid();
        return reads.read(()->global(token,true),a->{
            if(scopes.findById(id).isEmpty())throw missing();
            long total=appointments.countHistory(id,status);
            var items=w.offset()>=total?List.<Appointment>of():appointments.history(id,status,w.pageable()).stream().map(this::appointment).toList();
            return new Page<>(items,w.page(),w.size(),total);
        });
    }
    private CurrentIdentityReader.Admin global(Jwt token,boolean detail){var a=identity.admin(token,false);if(!a.account().superAdmin())throw new IdentityFailure(detail?404:403,detail?"NOT_FOUND":"FORBIDDEN");return a;}
    private Scope scope(ScopeEntity s){return new Scope(s.getId(),s.getCompanyId(),s.getDepartmentId(),scopes.systems(s.getId()),s.getVersion(),s.getCreatedAt());}
    private Appointment appointment(AppointmentRepository.View a){return new Appointment(a.getId(),a.getScopeId(),a.getAccountId(),a.getEmployeeId(),a.getStatus(),a.getVersion(),a.getScopeVersion(),a.getCompanyId(),a.getDepartmentId(),scopes.systems(a.getScopeId()));}
    private static IdentityFailure missing(){return new IdentityFailure(404,"NOT_FOUND");}
}
