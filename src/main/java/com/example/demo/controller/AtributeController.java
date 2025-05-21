package com.example.demo.controller;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.core.converters.CSVLoader;
import weka.core.converters.ConverterUtils.DataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.core.converters.CSVLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
@RestController
public class AtributeController {

    @PostMapping("/api/upload/attributes")
   public ResponseEntity<?> getAttributes(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("Por favor, selecciona un archivo.");
            }

            // Determinar la extensión original del archivo (si existe)
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            // Crear un archivo temporal con una extensión
            Path tempFile = Files.createTempFile("weka-upload", extension);
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            File tempFileObj = tempFile.toFile();

            Instances data = null;
            try {
                // Intentar cargar como CSV primero (puedes ajustar el orden)
                CSVLoader csvLoader = new CSVLoader();
                csvLoader.setSource(tempFileObj);
                data = csvLoader.getDataSet();
            } catch (IOException e1) {
                try {
                    // Si falla como CSV, intentar cargar como ARFF
                    ArffLoader arffLoader = new ArffLoader();
                    arffLoader.setSource(tempFileObj);
                    data = arffLoader.getDataSet();
                } catch (IOException e2) {
                    // Si ambos fallan, devolver un error
                    tempFileObj.delete();
                    return ResponseEntity.badRequest().body("Formato de archivo no soportado o inválido.");
                }
            }

            if (data == null) {
                tempFileObj.delete();
                return ResponseEntity.internalServerError().body("Error al cargar los datos con Weka.");
            }

            List<String> attributeNames = new ArrayList<>();
            for (int i = 0; i < data.numAttributes(); i++) {
                attributeNames.add(data.attribute(i).name());
            }

            tempFileObj.delete();
            return ResponseEntity.ok(new AttributeResponse(attributeNames));

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al procesar el archivo: " + e.getMessage());
        }
    }
}

class AttributeResponse {
    private List<String> attributes;

    public AttributeResponse(List<String> attributes) {
        this.attributes = attributes;
    }

    public List<String> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<String> attributes) {
        this.attributes = attributes;
    }
}