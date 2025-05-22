package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;
import java.util.logging.Logger;
import java.io.File;

@Configuration
public class AppConfig {
    private static final Logger logger = Logger.getLogger(AppConfig.class.getName());
    
    @Value("${server.port:8080}")
    private String serverPort;
    
    @Value("${spring.servlet.multipart.location:/tmp/weka-files}")
    private String tempDirPath;
    
    @PostConstruct
    public void init() {
        logger.info("Inicializando aplicación en puerto " + serverPort);
        
        // Asegurar que el directorio temporal exista y tenga permisos
        File tempDir = new File(tempDirPath);
        if (!tempDir.exists()) {
            logger.info("Creando directorio temporal: " + tempDirPath);
            if (tempDir.mkdirs()) {
                logger.info("Directorio temporal creado con éxito");
            } else {
                logger.warning("No se pudo crear el directorio temporal: " + tempDirPath);
            }
        }
        
        // Configurar la propiedad del sistema para el directorio temporal
        System.setProperty("java.io.tmpdir", tempDirPath);
        logger.info("Directorio temporal configurado en: " + System.getProperty("java.io.tmpdir"));
    }
}
