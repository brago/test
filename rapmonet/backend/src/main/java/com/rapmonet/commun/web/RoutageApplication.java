package com.rapmonet.commun.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Déploiement en un seul service : les écrans Angular compilés sont servis depuis classpath:/static. Une adresse
 * d'écran (ex. /rapprochement/gab-lis) renvoie index.html, le routage étant fait par le navigateur.
 */
@Configuration
class RoutageApplication implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String chemin, Resource emplacement) throws IOException {
                        Resource demande = emplacement.createRelative(chemin);
                        if (demande.exists() && demande.isReadable()) {
                            return demande;
                        }
                        if (chemin.startsWith("api/") || chemin.startsWith("actuator") || chemin.contains(".")) {
                            return null;
                        }
                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
