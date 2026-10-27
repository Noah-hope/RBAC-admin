package com.adgn.catalogfixture;

import com.adgn.backend.catalog.repository.CatalogRepository;
import com.adgn.backend.catalog.service.*;
import com.adgn.backend.identity.security.IdentityFailure;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CatalogPlannerContractTest {
    @Test void disablingNavigationIncludesItsDescendantPermissionInImpactWithoutMutatingInput() {
        var json=JsonMapper.builder().build();
        var parent=new CatalogRepository.Node("parent","system","NAV","导航","NAVIGATION",null,0,true,null,1);
        var function=new CatalogRepository.Node("function","system","FN","功能","FUNCTION","parent",0,true,"view",1);
        var access=new CatalogRepository.Permission("access","system","ACCESS","访问","SYSTEM_ACCESS",null,false,true,1);
        var view=new CatalogRepository.Permission("view","system","VIEW","查看","FUNCTION_OPERATION","function",true,true,1);
        var snapshot=new CatalogRepository.Snapshot(Map.of("parent",parent,"function",function),Map.of("access",access,"view",view));
        var command=json.createObjectNode().put("kind","NODE_UPDATE").put("id","parent").put("expectedVersion",1)
                .put("name","导航").putNull("parentId").put("sortOrder",0).put("enabled",false).putNull("visibilityPermissionId");
        var result=new CatalogPlanner(json).plan("system",1,snapshot,new CatalogInput.Batch(1,List.of(command),"fixture"),Map.of());
        assertThat(result.changed()).isTrue();assertThat(result.after().nodes().get("parent").enabled()).isFalse();
        assertThat(result.after().nodes().get("parent").version()).isEqualTo(2);
        assertThat(result.impact().touchedPermissions()).containsExactly("view");
        assertThat(snapshot.nodes().get("parent")).isEqualTo(parent);assertThat(parent.enabled()).isTrue();
    }
    @Test void unknownCommandIsRejectedWithoutChangingOriginalSnapshot() {
        var json=JsonMapper.builder().build();var snapshot=new CatalogRepository.Snapshot(Map.of(),Map.of());
        var command=json.createObjectNode().put("kind","UNSUPPORTED");
        assertThatThrownBy(()->new CatalogPlanner(json).plan("system",1,snapshot,
                new CatalogInput.Batch(1,List.of(command),"fixture"),Map.of()))
                .isInstanceOfSatisfying(IdentityFailure.class,e->assertThat(e.status()).isEqualTo(422));
        assertThat(snapshot.nodes()).isEmpty();assertThat(snapshot.permissions()).isEmpty();
    }
}
