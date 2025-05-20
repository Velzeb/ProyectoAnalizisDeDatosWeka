package com.example.demo.controller;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class CsvController {

   @PostMapping("/upload2")
public ResponseEntity<byte[]> handleFileUpload(@RequestParam("file") MultipartFile file) {
    try {
        // Leer el contenido del archivo y reemplazar ';' por ','
        BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line.replace(";", ",")).append("\n");
        }
        String cleanedCsv = sb.toString();

        // Leer el CSV con OpenCSV
        CSVReader csvReader = new CSVReader(new StringReader(cleanedCsv));
        List<String[]> allRows = csvReader.readAll();

        if (allRows.isEmpty()) {
            return ResponseEntity.badRequest().body("El archivo está vacío.".getBytes());
        }

        // Eliminar columnas duplicadas (basado en encabezado)
        String[] headers = allRows.get(0);
        Map<String, Integer> headerIndexMap = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].toLowerCase();
            if (!headerIndexMap.containsKey(header)) {
                headerIndexMap.put(header, i);
            }
        }

        List<String[]> deduplicatedRows = new ArrayList<>();
        for (String[] row : allRows) {
            List<String> newRow = new ArrayList<>();
            for (int index : headerIndexMap.values()) {
                if (index < row.length) {
                    newRow.add(row[index]);
                } else {
                    newRow.add(""); // Rellenar si falta valor
                }
            }
            deduplicatedRows.add(newRow.toArray(new String[0]));
        }

        // Limpiar datos
        List<String[]> cleanedRows = deduplicatedRows.stream()
                .filter(row -> Arrays.stream(row).noneMatch(cell -> cell == null || cell.trim().isEmpty()))
                .map(row -> Arrays.stream(row)
                        .map(cell -> cleanString(cell))
                        .toArray(String[]::new))
                .collect(Collectors.toList());

        // Escribir el CSV limpio
        StringWriter writer = new StringWriter();
        CSVWriter csvWriter = new CSVWriter(writer);
        csvWriter.writeAll(cleanedRows);
        csvWriter.close();
        String finalCsv = writer.toString();

        // Preparar la respuesta
        byte[] output = finalCsv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cleaned_dataset.csv\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(output);

    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.badRequest().body(("Error al procesar el archivo: " + e.getMessage()).getBytes());
    }
}

// Función auxiliar para limpiar texto: minúsculas, sin acentos ni caracteres raros
private String cleanString(String input) {
    if (input == null) return "";
    // Convertir a minúsculas
    String result = input.toLowerCase();

    // Eliminar acentos
    result = Normalizer.normalize(result, Normalizer.Form.NFD);
    result = result.replaceAll("\\p{M}", "");

    // Eliminar caracteres no alfanuméricos (excepto puntuación básica y espacio)
    result = result.replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}]", "");

    return result.trim();
}
}
