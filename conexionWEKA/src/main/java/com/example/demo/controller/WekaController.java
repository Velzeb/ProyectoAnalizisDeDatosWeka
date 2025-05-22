package com.example.demo.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.clusterers.SimpleKMeans;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;
import weka.classifiers.trees.J48;
import weka.classifiers.Evaluation;
import weka.classifiers.functions.MultilayerPerceptron;


import java.io.InputStream;
import java.io.InputStreamReader;

import weka.classifiers.trees.J48;
import weka.classifiers.Evaluation;
import weka.core.Instances;
import org.json.JSONObject;

@RestController
@RequestMapping("/api/analyze")
public class WekaController {

    @PostMapping("/upload")
    public String analyzeFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("method") String method,
            @RequestParam(value = "evaluation", required = false) String evaluationMethod,
            @RequestParam(value = "targetAttribute", required = false) String targetAttributeName,
            
            @RequestParam(value = "numClusters", required = false, defaultValue = "3") int numClusters) {
        try {
            // Determinar el tipo de archivo (CSV o ARFF) y cargar los datos adecuadamente
            Instances data;
            String fileName = file.getOriginalFilename();
            if (fileName != null && fileName.endsWith(".csv")) {
                data = loadCSV(file.getInputStream());
            } else {
                data = loadARFF(file.getInputStream());
            }

            // Establecer el índice de la clase, si existe
            if (data.classIndex() == -1 && data.numAttributes() > 1) {
                data.setClassIndex(data.numAttributes() - 1);
            }

            // Llamar al método correspondiente
            String result;
            switch (method) {
                case "kmeans":
                    result =performKMeans(data, numClusters);
                    break;
                case "classificationj48":
                    result = performClassificationJ48(data, evaluationMethod, targetAttributeName);
                    break;
                
                case "mlp":
                    result = performMLPClassification(data, evaluationMethod, targetAttributeName);
                    break;

                default:
                    result = "Método no reconocido.";
            }

            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return "Error durante el análisis: " + e.getMessage();
        }
    }

    private Instances loadCSV(InputStream inputStream) throws Exception {
        CSVLoader loader = new CSVLoader();
        loader.setSource(inputStream);
        return loader.getDataSet();
    }

    private Instances loadARFF(InputStream inputStream) throws Exception {
        return new Instances(new InputStreamReader(inputStream));
    }

    private String performKMeans(Instances data, int numClusters) {
        try {
            // Eliminar el atributo de clase antes de clustering
            Remove remove = new Remove();
            remove.setAttributeIndices(String.valueOf(data.classIndex() + 1)); // WEKA usa índices 1-based
            remove.setInputFormat(data);
            Instances dataWithoutClass = Filter.useFilter(data, remove);

            // Configurar y aplicar K-Means
            SimpleKMeans kMeans = new SimpleKMeans();
            kMeans.setNumClusters(numClusters); // Número de clusters deseado
            kMeans.buildClusterer(dataWithoutClass);

            // Crear objeto JSON para la respuesta
            JSONObject resultJson = new JSONObject();
            
            // Información general del modelo
            resultJson.put("numClusters", kMeans.getNumClusters());
            resultJson.put("squaredError", kMeans.getSquaredError());
            
            Instances centroids = kMeans.getClusterCentroids();
            
            // Seleccionar dos atributos para visualización 2D
            int attr1 = 0;
            int attr2 = 1;
            if (centroids.numAttributes() > 2) {
                // Usamos los dos primeros atributos para la visualización 2D
                // En una implementación más avanzada podrían seleccionarse los más relevantes
                attr1 = 0;
                attr2 = 1;
            }
            
            // Extraer nombres de atributos para la visualización
            String attr1Name = centroids.attribute(attr1).name();
            String attr2Name = centroids.attribute(attr2).name();
            resultJson.put("visualizationAttributes", new String[] {attr1Name, attr2Name});
            
            // Datos de centroides
            JSONObject[] centroidPoints = new JSONObject[centroids.numInstances()];
            for (int i = 0; i < centroids.numInstances(); i++) {
                JSONObject centroid = new JSONObject();
                centroid.put("cluster", i);
                centroid.put(attr1Name, centroids.instance(i).value(attr1));
                centroid.put(attr2Name, centroids.instance(i).value(attr2));
                centroidPoints[i] = centroid;
            }
            resultJson.put("centroids", centroidPoints);
            
            // Calcular tamaño de los clústeres y asignar puntos a clústeres
            int[] clusterSizes = new int[kMeans.getNumClusters()];
            JSONObject[] instancePoints = new JSONObject[dataWithoutClass.numInstances()];
            
            for (int i = 0; i < dataWithoutClass.numInstances(); i++) {
                int cluster = kMeans.clusterInstance(dataWithoutClass.instance(i));
                clusterSizes[cluster]++;
                
                // Datos para la visualización de puntos
                JSONObject point = new JSONObject();
                point.put("cluster", cluster);
                point.put(attr1Name, dataWithoutClass.instance(i).value(attr1));
                point.put(attr2Name, dataWithoutClass.instance(i).value(attr2));
                instancePoints[i] = point;
            }
            
            resultJson.put("points", instancePoints);
            
            // Estadísticas de los clústeres
            JSONObject[] clusterStatsJson = new JSONObject[clusterSizes.length];
            for (int i = 0; i < clusterSizes.length; i++) {
                JSONObject clusterStat = new JSONObject();
                clusterStat.put("cluster", i);
                clusterStat.put("size", clusterSizes[i]);
                clusterStat.put("percentage", (clusterSizes[i] * 100.0 / dataWithoutClass.numInstances()));
                clusterStatsJson[i] = clusterStat;
            }
            resultJson.put("clusterStats", clusterStatsJson);
            
            // Contar las instancias incorrectas
            double incorrectCount = 0;
            for (int i = 0; i < data.numInstances(); i++) {
                // Obtener el clúster al que se asigna la instancia
                int cluster = kMeans.clusterInstance(dataWithoutClass.instance(i));

                // Obtener la clase real de la instancia
                double realClassValue = data.instance(i).classValue();

                // Si el clúster asignado no coincide con la clase real, se considera incorrecto
                if (cluster != realClassValue) {
                    incorrectCount++;
                }
            }
            
            resultJson.put("incorrectCount", incorrectCount);
            resultJson.put("incorrectPercentage", (incorrectCount / data.numInstances()) * 100);
            
            return resultJson.toString(2); // Indentado bonito
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\":\"Error al realizar K-Means: " + e.getMessage() + "\"}";
        }
    }


/*     
public String performClassificationJ48(Instances data, String evaluationMethod) {
    try {
        // Crear un clasificador J48
        J48 j48 = new J48();
        j48.buildClassifier(data);

        // Evaluar el modelo
        Evaluation eval;
        if ("cross-validation".equalsIgnoreCase(evaluationMethod)) {
            eval = new Evaluation(data);
            eval.crossValidateModel(j48, data, 10, new java.util.Random(1));
        } else {
            eval = new Evaluation(data);
            eval.evaluateModel(j48, data);
        }

        // Crear el JSON con las secciones
        JSONObject resultJson = new JSONObject();

        resultJson.put("resumenEvaluacion", eval.toSummaryString("", false));
        resultJson.put("precisionPorClase", eval.toClassDetailsString());
        resultJson.put("matrizConfusion", eval.toMatrixString());
        resultJson.put("arbolDecision", j48.graph());
        

        return resultJson.toString(2); // Indentado bonito
    } catch (Exception e) {
        e.printStackTrace();
        return "{\"error\":\"Error al realizar la clasificación: " + e.getMessage() + "\"}";
    }
}

*/
public String performClassificationJ48(Instances data, String evaluationMethod, String targetAttributeName) {
    try {
        // Verificar si el atributo target existe
        if (data.attribute(targetAttributeName) == null) {
            return "{\"error\":\"El atributo target '" + targetAttributeName + "' no existe en los datos.\"}";
        }

        // Establecer el atributo class (target)
        data.setClass(data.attribute(targetAttributeName));

        // Crear un clasificador J48
        J48 j48 = new J48();
        j48.buildClassifier(data);

        // Evaluar el modelo
        Evaluation eval;
        if ("cross-validation".equalsIgnoreCase(evaluationMethod)) {
            eval = new Evaluation(data);
            eval.crossValidateModel(j48, data, 10, new java.util.Random(1));
        } else {
            eval = new Evaluation(data);
            eval.evaluateModel(j48, data);
        }

        // Crear el JSON con las secciones
        JSONObject resultJson = new JSONObject();

        resultJson.put("resumenEvaluacion", eval.toSummaryString("", false));
        resultJson.put("precisionPorClase", eval.toClassDetailsString());
        resultJson.put("matrizConfusion", eval.toMatrixString());
        resultJson.put("arbolDecision", j48.graph());

        return resultJson.toString(2); // Indentado bonito
    } catch (Exception e) {
        e.printStackTrace();
        return "{\"error\":\"Error al realizar la clasificación: " + e.getMessage() + "\"}";
    }
}

/*  
private String performMLPClassification(Instances data, String evaluationMethod) {
    try {
        // Crear y configurar el clasificador MLP
        MultilayerPerceptron mlp = new MultilayerPerceptron();
        mlp.setLearningRate(0.3);
        mlp.setMomentum(0.2);
        mlp.setTrainingTime(500);
        mlp.setHiddenLayers("a"); // 'a' para (atributos + clases) / 2

        // Construir el clasificador
        mlp.buildClassifier(data);

        // Evaluar el modelo
        Evaluation eval;
        if ("cross-validation".equalsIgnoreCase(evaluationMethod)) {
            eval = new Evaluation(data);
            eval.crossValidateModel(mlp, data, 10, new java.util.Random(1));
        } else {
            eval = new Evaluation(data);
            eval.evaluateModel(mlp, data);
        }

        // Construir el resultado
        StringBuilder result = new StringBuilder("Resultados de la Clasificación con MLP:\n");
        result.append(eval.toSummaryString("\nResumen de la Evaluación\n", false));
        result.append("\n\n=== Precisión Detallada por Clase ===\n");
        result.append(eval.toClassDetailsString());
        result.append("\n\n=== Matriz de Confusión ===\n");
        result.append(eval.toMatrixString());

        return result.toString();
    } catch (Exception e) {
        e.printStackTrace();
        return "Error al realizar la clasificación con MLP: " + e.getMessage();
    }
}
*/
private String performMLPClassification(Instances data, String evaluationMethod, String targetAttributeName) {
    try {
        // Verificar si el atributo target existe
        if (data.attribute(targetAttributeName) == null) {
            return "{\"error\":\"El atributo target '" + targetAttributeName + "' no existe en los datos.\"}";
        }

        // Establecer el atributo class (target)
        data.setClass(data.attribute(targetAttributeName));

        // Crear y configurar el clasificador MLP
        MultilayerPerceptron mlp = new MultilayerPerceptron();
        mlp.setLearningRate(0.3);
        mlp.setMomentum(0.2);
        mlp.setTrainingTime(500);
        mlp.setHiddenLayers("a"); // 'a' para (atributos + clases) / 2

        // Construir el clasificador
        mlp.buildClassifier(data);

        // Evaluar el modelo
        Evaluation eval;
        if ("cross-validation".equalsIgnoreCase(evaluationMethod)) {
            eval = new Evaluation(data);
            eval.crossValidateModel(mlp, data, 10, new java.util.Random(1));
        } else {
            eval = new Evaluation(data);
            eval.evaluateModel(mlp, data);
        }

        // Crear objeto JSON para la respuesta
        JSONObject resultJson = new JSONObject();
        
        // Datos de evaluación básicos
        resultJson.put("resumenEvaluacion", eval.toSummaryString("", false));
        resultJson.put("precisionPorClase", eval.toClassDetailsString());
        resultJson.put("matrizConfusion", eval.toMatrixString());
        
        // Datos para la visualización de la red neuronal
        JSONObject networkJson = new JSONObject();
        
        // Número de neuronas en cada capa
        int inputLayerSize = data.numAttributes() - 1; // Todos los atributos menos la clase
        int outputLayerSize = data.attribute(data.classIndex()).numValues(); // Número de clases
        
        // Determinar el número de neuronas en la capa oculta
        // Esta es una aproximación basada en la configuración "a" (atributos + clases) / 2
        int hiddenLayerSize = (inputLayerSize + outputLayerSize) / 2;
        
        // Configuración de la red para visualización
        networkJson.put("inputLayer", inputLayerSize);
        networkJson.put("hiddenLayers", new int[]{hiddenLayerSize});
        networkJson.put("outputLayer", outputLayerSize);
        networkJson.put("learningRate", 0.3);
        networkJson.put("momentum", 0.2);
        networkJson.put("trainingTime", 500);
        
        // Agregar datos de precisión para la visualización
        networkJson.put("accuracy", 100 - eval.errorRate() * 100);
        
        resultJson.put("networkStructure", networkJson);

        return resultJson.toString(2); // Indentado bonito
    } catch (Exception e) {
        e.printStackTrace();
        return "{\"error\":\"Error al realizar la clasificación con MLP: " + e.getMessage() + "\"}";
    }
}
}

