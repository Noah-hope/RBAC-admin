package com.adgn.backend.web.imports.config;

import com.adgn.backend.imports.ImportFileParser;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class ImportMultipartConfiguration {
    @Bean @ConditionalOnMissingBean(MultipartConfigElement.class)
    MultipartConfigElement multipartConfigElement(){return new MultipartConfigElement("",ImportFileParser.MAX_FILE,11L*1024*1024,0);}
}
