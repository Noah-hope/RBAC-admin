package com.rbac.rbac0;
import com.rbac.rbac0.dto.ApiDtos.ApiInput;
import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.entity.*;
import com.rbac.rbac0.mapper.*;
import com.rbac.rbac0.service.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.*;
@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({ApiService.class,OperationLogService.class})
class ApiPersistenceTest {
    @Autowired ApiService service;
    @Autowired ApplicationMapper apps;
    @Autowired PermissionMapper permissions;
    @Autowired ApiMapper apis;
    @Autowired ApiPermissionMapper links;
    private final SessionUser actor=new SessionUser(1,"admin","管理员",null,"TOTAL_ADMIN");
    @Test void sharedApiSurvivesUnlinkAndCanBeRemovedIndependently() {
        var app=new BusinessApplication();app.name="课程";app.code="course";apps.save(app);
        var first=permission(app.id,"course:view");var second=permission(app.id,"course:edit");
        var api=service.create(actor,app.id,new ApiInput("listCourses","GET","/courses"));
        service.replaceLinks(app.id,first.id,List.of(api.id()));service.replaceLinks(app.id,second.id,List.of(api.id()));
        service.replaceLinks(app.id,first.id,List.of());
        assertThat(apis.existsById(api.id())).isTrue();
        assertThat(links.findByApiId(api.id())).extracting(l->l.permissionId).containsExactly(second.id);
        service.delete(actor,app.id,api.id());
        assertThat(links.findByApiId(api.id())).isEmpty();assertThat(permissions.existsById(second.id)).isTrue();
    }
    @Test void crossApplicationLinksAreRejected() {
        var app=new BusinessApplication();app.name="课程";app.code="course";apps.save(app);
        var api=service.create(actor,app.id,new ApiInput("listCourses","GET","/courses"));
        assertThatThrownBy(()->service.replaceLinks(app.id+1,1,List.of(api.id()))).isInstanceOf(ManagementFailure.class);
    }
    private Permission permission(long app,String code) {
        var p=new Permission();p.applicationId=app;p.name=code;p.code=code;p.type="MENU";return permissions.save(p);
    }
}
