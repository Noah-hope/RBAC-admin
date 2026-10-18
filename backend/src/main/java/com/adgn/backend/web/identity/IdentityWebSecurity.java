package com.adgn.backend.web.identity;

import com.adgn.backend.identity.security.*;
import com.adgn.backend.identity.service.IdentityService;
import com.adgn.backend.core.transaction.PersistenceFailure;
import com.adgn.backend.web.problem.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;

@Configuration(proxyBeanMethods=false)
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class IdentityWebSecurity {
    @Bean SecurityFilterChain identityFilterChain(HttpSecurity http,ObjectProvider<IdentityService> services,ProblemResponses problems) throws Exception {
        http.csrf(c->c.disable()).formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable())
                .requestCache(c->c.disable()).sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(c->c.anyRequest().permitAll())
                .addFilterBefore(new IdentityFilter(services,problems),AnonymousAuthenticationFilter.class);
        return http.build();
    }
    private static final class IdentityFilter extends OncePerRequestFilter {
        private final ObjectProvider<IdentityService> services;
        private final ProblemResponses problems;
        private IdentityFilter(ObjectProvider<IdentityService> services,ProblemResponses problems){this.services=services;this.problems=problems;}
        @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
            // Match the same decoded path used by servlet routing, including encoded letters.
            String path=request.getServletPath();
            if(path.isEmpty())path=org.springframework.web.util.UriUtils.decode(
                    request.getRequestURI().substring(request.getContextPath().length()),java.nio.charset.StandardCharsets.UTF_8);
            IdentityTokens.Family family=family(path);
            if(family==null){chain.doFilter(request,response);return;}
            var service=services.getIfAvailable();
            if(service==null){failure(new IdentityFailure(503,"DEPENDENCY_UNAVAILABLE"),request,response);return;}
            boolean login=path.equals("/api/v1/admin/sessions") && request.getMethod().equals("POST");
            boolean logout=path.equals("/api/v1/admin/sessions/current") && request.getMethod().equals("DELETE");
            if(login || logout){chain.doFilter(request,response);return;}
            try {
                var headers=Collections.list(request.getHeaders("Authorization"));
                if(headers.size()!=1 || !headers.getFirst().regionMatches(true,0,"Bearer ",0,7))throw IdentityFailure.authentication();
                var token=service.verify(headers.getFirst().substring(7),family);
                service.check(token,family,path.equals("/api/v1/admin/password") && request.getMethod().equals("PUT"));
                SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(family.name()))));
            } catch(IdentityFailure denied){failure(denied,request,response);return;}
            catch(PersistenceFailure unavailable){problems.write(response,HttpStatusCode.valueOf(unavailable.kind()==PersistenceFailure.Kind.DEPENDENCY_UNAVAILABLE?503:500),List.of(),request);return;}
            catch(Exception unexpected){problems.write(response,HttpStatusCode.valueOf(503),List.of(),request);return;}
            chain.doFilter(request,response);
        }
        private static IdentityTokens.Family family(String path){
            if(path.equals("/api/v1/admin") || path.startsWith("/api/v1/admin/"))return IdentityTokens.Family.ADMIN;
            if(path.equals("/api/v1/integration") || path.startsWith("/api/v1/integration/"))return IdentityTokens.Family.BUSINESS;
            if(path.equals("/api/v1/oa") || path.startsWith("/api/v1/oa/"))return IdentityTokens.Family.OA;
            return null;
        }
        private void failure(IdentityFailure error,HttpServletRequest request,HttpServletResponse response) throws IOException {
            response.setStatus(error.status()); response.setContentType("application/problem+json"); response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control","no-store");
            if(error.status()==401)response.setHeader("WWW-Authenticate","Bearer");
            if(error.retryAfter()>0)response.setHeader("Retry-After",Long.toString(error.retryAfter()));
            var template=problems.body(HttpStatusCode.valueOf(error.status()),List.of(),request);
            var body=new ApiProblem("urn:rbac-admin:problem:"+error.code().toLowerCase(Locale.ROOT).replace('_','-'),
                    template.title(),error.status(),error.code(),template.detail(),template.requestId(),template.errors());
            // Use the application's JSON mapping and existing public Problem contract.
            response.getWriter().write(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(body));
        }
    }
}
