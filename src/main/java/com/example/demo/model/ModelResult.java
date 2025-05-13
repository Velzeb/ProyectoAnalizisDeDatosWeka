package com.example.demo.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModelResult {
    private String textResult;
    private List<String> images;

    public ModelResult() {
        this.images = new ArrayList<>();
    }

    public ModelResult(String textResult) {
        this.textResult = textResult;
        this.images = new ArrayList<>();
    }

    public String getTextResult() {
        return textResult;
    }

    public void setTextResult(String textResult) {
        this.textResult = textResult;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }

    public void addImage(String base64Image) {
        if (this.images == null) {
            this.images = new ArrayList<>();
        }
        this.images.add(base64Image);
    }

    // Método para convertir a Map para serialización JSON
    public Map<String, Object> toMap() {
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("textResult", this.textResult);
        resultMap.put("images", this.images);
        return resultMap;
    }
}
