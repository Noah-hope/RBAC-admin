package com.adgn.backend.scope.service;

import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.management.ManagementInput;
import java.util.*;

public record ScopeCreationInput(String companyId,String departmentId,List<String> systemIds,List<String> accountIds,String reason) {
    public static ScopeCreationInput normalized(ScopeCreationInput input){
        if(input==null)throw IdentityFailure.invalid();
        return new ScopeCreationInput(ManagementInput.identifier(input.companyId()),ManagementInput.identifier(input.departmentId()),ids(input.systemIds()),ids(input.accountIds()),ManagementInput.text(input.reason(),1,500));
    }
    private static List<String> ids(List<String> values){
        if(values==null)throw IdentityFailure.invalid();var result=new TreeSet<String>();
        for(String value:values)if(!result.add(ManagementInput.identifier(value)))throw IdentityFailure.invalid();
        return List.copyOf(result);
    }
}
