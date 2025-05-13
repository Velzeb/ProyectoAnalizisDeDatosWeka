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
import java.util.Arrays;
import java.util.List;
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

            // Eliminar filas con valores faltantes
            List<String[]> cleanedRows = allRows.stream()
                    .filter(row -> Arrays.stream(row).noneMatch(cell -> cell == null || cell.trim().isEmpty()))
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
}
