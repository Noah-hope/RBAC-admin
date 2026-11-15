package com.rbac.rbac0.service;
import com.rbac.rbac0.dto.ApiDtos.PermissionSummary;
import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.mapper.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class PermissionCatalogService {
    private final ApplicationMapper apps;
    private final PermissionMapper permissions;
    public PermissionCatalogService(ApplicationMapper apps,PermissionMapper permissions) {this.apps=apps;this.permissions=permissions;}
    public record Catalog(List<Application> applications) {}
    public record Application(long id,String name,String code,List<PermissionSummary> permissions) {}
    @Transactional(readOnly=true)
    public Catalog catalog(SessionUser actor) {
        DepartmentService.requireAdministrator(actor);
        var all=permissions.findAll();
        return new Catalog(apps.findAll(org.springframework.data.domain.Sort.by("id")).stream().map(a->new Application(a.id,a.name,a.code,
                all.stream().filter(p->p.applicationId.equals(a.id)).map(PermissionSummary::of).toList())).toList());
    }
}
