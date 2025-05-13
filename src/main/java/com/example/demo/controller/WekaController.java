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

@RestController
@RequestMapping("/api/analyze")
public class WekaController {

    @PostMapping("/upload")
    public String analyzeFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("method") String method,
            @RequestParam(value = "evaluation", required = false) String evaluationMethod,
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
                    result = performClassificationJ48(data, evaluationMethod);
                    break;
                
                case "mlp":
                    result = performMLPClassification(data, evaluationMethod);
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

            // Crear el resultado del análisis
            StringBuilder result = new StringBuilder("kMeans\n======\n\n");

            result.append("Number of clusters: ").append(kMeans.getNumClusters()).append("\n");
            result.append("Within cluster sum of squared errors: ").append(kMeans.getSquaredError()).append("\n\n");

            result.append("Final cluster centroids:\n");
            Instances centroids = kMeans.getClusterCentroids();
            for (int i = 0; i < centroids.numInstances(); i++) {
                result.append("Cluster ").append(i).append(": ");
                for (int j = 0; j < centroids.numAttributes(); j++) {
                    result.append(centroids.instance(i).value(j)).append(", ");
                }
                result.append("\n");
            }

            // Calcular manualmente el tamaño de los clústeres
            int[] clusterSizes = new int[kMeans.getNumClusters()];
            for (int i = 0; i < dataWithoutClass.numInstances(); i++) {
                int cluster = kMeans.clusterInstance(dataWithoutClass.instance(i));
                clusterSizes[cluster]++;
            }

            result.append("\nClustered Instances:\n");
            for (int i = 0; i < clusterSizes.length; i++) {
                result.append("Cluster ").append(i).append(": ").append(clusterSizes[i])
                      .append(" (").append((clusterSizes[i] * 100.0 / dataWithoutClass.numInstances())).append("%)\n");
            }

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

            // Mostrar la cantidad de instancias incorrectamente clasificadas
            result.append("\nIncorrectly clustered instances: ").append(incorrectCount).append(" (")
                  .append((incorrectCount / data.numInstances()) * 100).append("%)\n");

            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error al realizar K-Means: " + e.getMessage();
        }
    }

    private String performClassificationJ48(Instances data, String evaluationMethod) {
    try {
        // Crear un clasificador J48 (C4.5)
        J48 j48 = new J48();
        j48.buildClassifier(data);

        // Evaluar el modelo
        Evaluation eval;
        if ("cross-validation".equalsIgnoreCase(evaluationMethod)) {
            eval = new Evaluation(data);
            eval.crossValidateModel(j48, data, 10, new java.util.Random(1));
        } else { // Por defecto: usar conjunto de entrenamiento
            eval = new Evaluation(data);
            eval.evaluateModel(j48, data);
        }

        // Crear un StringBuilder para los resultados
        StringBuilder result = new StringBuilder("Resultados de la Clasificación:\n");

        // Resultado de la evaluación general
        result.append(eval.toSummaryString("\nResultados de la Evaluación\n", false));

        // Detalles de la precisión por clase
        result.append("\n\n=== Precisión Detallada Por Clase ===\n");
        result.append(eval.toClassDetailsString());

        // Matriz de confusión
        result.append("\n\n=== Matriz de Confusión ===\n");
        result.append(eval.toMatrixString());

        // Representación del árbol de decisión
        result.append("\n\n=== Árbol de Decisión ===\n");
        result.append(j48.toString());
        

        return result.toString();
    } catch (Exception e) {
        e.printStackTrace();
        return "Error al realizar la clasificación: " + e.getMessage();
    }
}


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

}

