package com.example.demo.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import com.example.demo.model.ModelResult;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.clusterers.SimpleKMeans;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;
import weka.classifiers.trees.J48;
import weka.classifiers.Evaluation;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.evaluation.ThresholdCurve;
import weka.gui.treevisualizer.PlaceNode2;
import weka.gui.treevisualizer.TreeVisualizer;
import weka.gui.visualize.PlotData2D;
import weka.gui.visualize.ThresholdVisualizePanel;
import weka.gui.visualize.VisualizePanel;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Random;
import java.util.Base64;
import javax.imageio.ImageIO;

@RestController
@RequestMapping("/api/analyze")
public class WekaController {

    // Directorio para guardar imágenes temporales
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");

    @PostMapping(value = "/upload", produces = MediaType.APPLICATION_JSON_VALUE)
    public ModelResult analyzeFile(
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
            ModelResult result = new ModelResult();
            switch (method) {
                case "kmeans":
                    result = performKMeans(data, numClusters);
                    break;
                case "classificationj48":
                    result = performClassificationJ48(data, evaluationMethod);
                    break;
                
                case "mlp":
                    result = performMLPClassification(data, evaluationMethod);
                    break;

                default:
                    result.setTextResult("Método no reconocido.");
            }

            return result;
        } catch (Exception e) {
            e.printStackTrace();
            ModelResult errorResult = new ModelResult();
            errorResult.setTextResult("Error durante el análisis: " + e.getMessage());
            return errorResult;
        }
    }
    
    // Método auxiliar para guardar imagen como Base64
    private String saveImageAsBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    private Instances loadCSV(InputStream inputStream) throws Exception {
        CSVLoader loader = new CSVLoader();
        loader.setSource(inputStream);
        return loader.getDataSet();
    }

    private Instances loadARFF(InputStream inputStream) throws Exception {
        return new Instances(new InputStreamReader(inputStream));
    }

    private ModelResult performKMeans(Instances data, int numClusters) {
        ModelResult modelResult = new ModelResult();
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

            modelResult.setTextResult(result.toString());
            
            // Crear visualización del clustering
            if (dataWithoutClass.numAttributes() >= 2) {
                // Creamos un gráfico de dispersión para visualizar los clusters
                VisualizePanel visPanel = new VisualizePanel();
                PlotData2D plotData = new PlotData2D(dataWithoutClass);
                
                // Configurar los colores según el cluster asignado
                int[] clusterAssignments = new int[dataWithoutClass.numInstances()];
                for (int i = 0; i < dataWithoutClass.numInstances(); i++) {
                    clusterAssignments[i] = kMeans.clusterInstance(dataWithoutClass.instance(i));
                }
                
                plotData.setPlotName("K-Means Clustering");
                
                visPanel.addPlot(plotData);
                visPanel.setSize(600, 400);
                
                // Generar imagen y guardar como base64
                BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
                visPanel.paint(image.getGraphics());
                modelResult.addImage(saveImageAsBase64(image));
            }            return modelResult;
        } catch (Exception e) {
            e.printStackTrace();
            modelResult.setTextResult("Error al realizar K-Means: " + e.getMessage());
            return modelResult;
        }
    }    private ModelResult performClassificationJ48(Instances data, String evaluationMethod) {
    ModelResult modelResult = new ModelResult();
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
        
        modelResult.setTextResult(result.toString());
        
        // Generar visualización del árbol de decisión
        try {
            // Visualización del árbol de decisión
            TreeVisualizer treeVisualizer = new TreeVisualizer(null, j48.graph(), 
                                                            new PlaceNode2());
            treeVisualizer.setSize(800, 600);
            
            // Generar imagen del árbol
            BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
            treeVisualizer.paint(image.getGraphics());
            modelResult.addImage(saveImageAsBase64(image));
            
            // Generar curva ROC para la primera clase (si es clasificación binaria)
            if (data.classAttribute().numValues() == 2) {
                ThresholdCurve tc = new ThresholdCurve();
                int classIndex = 0; // Primera clase
                Instances tcurve = tc.getCurve(eval.predictions(), classIndex);
                
                // Visualizar curva ROC
                ThresholdVisualizePanel tvp = new ThresholdVisualizePanel();
                tvp.setROCString("ROC - Clase: " + data.classAttribute().value(classIndex));
                tvp.setName("Curva ROC");
                
                PlotData2D plotdata = new PlotData2D(tcurve);
                plotdata.setPlotName("ROC");
                plotdata.m_alwaysDisplayPointsOfThisSize = 10;
                tvp.addPlot(plotdata);
                tvp.setSize(600, 400);
                
                BufferedImage rocImage = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
                tvp.paint(rocImage.getGraphics());
                modelResult.addImage(saveImageAsBase64(rocImage));
            }
        } catch (Exception e) {
            System.out.println("Error al generar visualizaciones: " + e.getMessage());
            e.printStackTrace();
        }

        return modelResult;    } catch (Exception e) {
        e.printStackTrace();
        modelResult.setTextResult("Error al realizar la clasificación: " + e.getMessage());
        return modelResult;
    }
}
    private ModelResult performMLPClassification(Instances data, String evaluationMethod) {
    ModelResult modelResult = new ModelResult();
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
        
        // Añadir información sobre la arquitectura de la red
        result.append("\n\n=== Arquitectura de la Red Neural ===\n");
        result.append(mlp.toString());

        modelResult.setTextResult(result.toString());
        
        // Generar visualizaciones
        try {
            // Generar curva ROC para varias clases
            for (int classIndex = 0; classIndex < data.classAttribute().numValues(); classIndex++) {
                if (classIndex > 2) break; // Limitar a 3 curvas máximo para no sobrecargar
                
                ThresholdCurve tc = new ThresholdCurve();
                Instances tcurve = tc.getCurve(eval.predictions(), classIndex);
                
                // Visualizar curva ROC
                ThresholdVisualizePanel tvp = new ThresholdVisualizePanel();
                tvp.setROCString("ROC - Clase: " + data.classAttribute().value(classIndex));
                tvp.setName("Curva ROC");
                
                PlotData2D plotdata = new PlotData2D(tcurve);
                plotdata.setPlotName("ROC para clase " + data.classAttribute().value(classIndex));
                plotdata.m_alwaysDisplayPointsOfThisSize = 10;
                tvp.addPlot(plotdata);
                tvp.setSize(600, 400);
                
                BufferedImage rocImage = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
                tvp.paint(rocImage.getGraphics());
                modelResult.addImage(saveImageAsBase64(rocImage));
            }
            
            // Mostrar gráfico de predicciones vs reales (para las primeras instancias)
            int numInstancesToShow = Math.min(30, data.numInstances());
            VisualizePanel predVsActual = new VisualizePanel();
            PlotData2D plotPredictions = new PlotData2D(data);
            plotPredictions.setPlotName("Predicciones vs. Valores Reales");
            predVsActual.addPlot(plotPredictions);
            predVsActual.setSize(600, 400);
            
            BufferedImage predVsActualImage = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
            predVsActual.paint(predVsActualImage.getGraphics());
            modelResult.addImage(saveImageAsBase64(predVsActualImage));
            
        } catch (Exception e) {
            System.out.println("Error al generar visualizaciones: " + e.getMessage());
            e.printStackTrace();
        }        return modelResult;
        } catch (Exception e) {
            e.printStackTrace();
            modelResult.setTextResult("Error al realizar la clasificación con MLP: " + e.getMessage());
            return modelResult;
        }
    }

}

