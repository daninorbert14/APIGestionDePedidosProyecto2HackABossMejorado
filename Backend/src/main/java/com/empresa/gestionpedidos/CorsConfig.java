package com.empresa.gestionpedidos;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/* Esto es para que el navegador permita que el frontend pueda hacer peticiones al backend.
Como el frontend y el backend corren en puertos diferentes (5173 vs 8080) sin este código
 el navegador bloquearía todas las llamadas fetch con un error de CORS en la consola. */
@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // Aplica esta regla a todos los endpoints que empiecen por /api/
                registry.addMapping("/api/**")
                        // Solo permite peticiones que vengan exactamente de esa URL
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                        // Permite cualquier cabecera, incluyendo el Content-Type:
                        // application/json que mandamos en los POST/PUT/PATCH
                        .allowedHeaders("*");
            }
        };
    }
}
