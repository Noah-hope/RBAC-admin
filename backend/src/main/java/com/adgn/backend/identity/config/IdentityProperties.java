package com.adgn.backend.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rbac.identity")
public class IdentityProperties {
    private String adminKeyId,adminJwkSet,businessJwkSet,oaJwkSet;
    private String adminIssuer="urn:rbac:admin",businessIssuer="urn:rbac:business",oaIssuer="urn:rbac:oa";
    private int loginIpLimit=10,loginAccountLimit=10;
    public String getAdminKeyId(){return adminKeyId;} public void setAdminKeyId(String v){adminKeyId=v;}
    public String getAdminJwkSet(){return adminJwkSet;} public void setAdminJwkSet(String v){adminJwkSet=v;}
    public String getBusinessJwkSet(){return businessJwkSet;} public void setBusinessJwkSet(String v){businessJwkSet=v;}
    public String getOaJwkSet(){return oaJwkSet;} public void setOaJwkSet(String v){oaJwkSet=v;}
    public String getAdminIssuer(){return adminIssuer;} public void setAdminIssuer(String v){adminIssuer=v;}
    public String getBusinessIssuer(){return businessIssuer;} public void setBusinessIssuer(String v){businessIssuer=v;}
    public String getOaIssuer(){return oaIssuer;} public void setOaIssuer(String v){oaIssuer=v;}
    public int getLoginIpLimit(){return loginIpLimit;} public void setLoginIpLimit(int v){loginIpLimit=v;}
    public int getLoginAccountLimit(){return loginAccountLimit;} public void setLoginAccountLimit(int v){loginAccountLimit=v;}
}
