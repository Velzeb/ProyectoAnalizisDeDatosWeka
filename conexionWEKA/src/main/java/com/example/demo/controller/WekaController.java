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

import org.json.JSONObject;
import org.json.JSONArray;

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
    }    private String performKMeans(Instances data, int numClusters) {
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
            
            // Explicación general para usuario no especializado
            resultJson.put("explicacion", "El análisis de agrupamiento (K-Means) ha identificado " + 
                numClusters + " grupos de datos similares. Cada grupo contiene elementos con características parecidas entre sí.");
            
            // Información general del modelo en lenguaje sencillo
            resultJson.put("numClusters", kMeans.getNumClusters());
            resultJson.put("calidadAgrupamiento", "El error cuadrático de " + 
                String.format("%.2f", kMeans.getSquaredError()) + 
                " indica qué tan compactos son los grupos (valores más pequeños indican grupos más definidos).");
            resultJson.put("squaredError", kMeans.getSquaredError()); // Mantenemos el valor técnico también
            
            Instances centroids = kMeans.getClusterCentroids();
            
            // Seleccionar dos atributos para visualización 2D
            int attr1 = 0;
            int attr2 = 1;
            if (centroids.numAttributes() > 2) {
                // Usamos los dos primeros atributos para la visualización 2D
                attr1 = 0;
                attr2 = 1;
            }
            
            // Extraer nombres de atributos para la visualización
            String attr1Name = centroids.attribute(attr1).name();
            String attr2Name = centroids.attribute(attr2).name();
            resultJson.put("visualizationAttributes", new String[] {attr1Name, attr2Name});
            resultJson.put("atributosExplicacion", "El gráfico muestra la relación entre '" + 
                attr1Name + "' y '" + attr2Name + "', donde cada color representa un grupo distinto.");
            
            // Datos de centroides con explicación
            JSONObject[] centroidPoints = new JSONObject[centroids.numInstances()];
            for (int i = 0; i < centroids.numInstances(); i++) {
                JSONObject centroid = new JSONObject();
                centroid.put("cluster", i);
                centroid.put("nombre", "Grupo " + (i+1));
                centroid.put(attr1Name, centroids.instance(i).value(attr1));
                centroid.put(attr2Name, centroids.instance(i).value(attr2));
                centroid.put("explicacion", "El centro del Grupo " + (i+1) + " tiene estas características representativas.");
                centroidPoints[i] = centroid;
            }
            resultJson.put("centroides", centroidPoints);
            resultJson.put("centroidesExplicacion", "Los puntos grandes representan el centro o valor promedio de cada grupo.");
            
            // Calcular tamaño de los clústeres y asignar puntos a clústeres
            int[] clusterSizes = new int[kMeans.getNumClusters()];
            JSONObject[] instancePoints = new JSONObject[dataWithoutClass.numInstances()];
            
            for (int i = 0; i < dataWithoutClass.numInstances(); i++) {
                int cluster = kMeans.clusterInstance(dataWithoutClass.instance(i));
                clusterSizes[cluster]++;
                
                // Datos para la visualización de puntos
                JSONObject point = new JSONObject();
                point.put("cluster", cluster);
                point.put("grupo", "Grupo " + (cluster+1));
                point.put(attr1Name, dataWithoutClass.instance(i).value(attr1));
                point.put(attr2Name, dataWithoutClass.instance(i).value(attr2));
                instancePoints[i] = point;
            }
            
            resultJson.put("points", instancePoints);
            
            // Estadísticas de los clústeres en lenguaje sencillo
            JSONObject[] clusterStatsJson = new JSONObject[clusterSizes.length];
            StringBuilder distribucionTexto = new StringBuilder("Distribución de datos por grupo: ");
            
            for (int i = 0; i < clusterSizes.length; i++) {
                double percentage = clusterSizes[i] * 100.0 / dataWithoutClass.numInstances();
                
                JSONObject clusterStat = new JSONObject();
                clusterStat.put("cluster", i);
                clusterStat.put("nombre", "Grupo " + (i+1));
                clusterStat.put("tamaño", clusterSizes[i]);
                clusterStat.put("porcentaje", percentage);
                clusterStat.put("descripcion", String.format("El Grupo %d contiene %d elementos (%.1f%% del total)", 
                    (i+1), clusterSizes[i], percentage));
                clusterStatsJson[i] = clusterStat;
                
                distribucionTexto.append(String.format("Grupo %d: %d elementos (%.1f%%), ", 
                    (i+1), clusterSizes[i], percentage));
            }
            
            resultJson.put("estadisticasGrupos", clusterStatsJson);
            resultJson.put("distribucionResumen", distribucionTexto.toString().replaceAll(", $", "."));
            
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
            
            double incorrectPercentage = (incorrectCount / data.numInstances()) * 100;
            resultJson.put("incorrectCount", incorrectCount);
            resultJson.put("incorrectPercentage", incorrectPercentage);
            resultJson.put("efectividadAgrupamiento", String.format(
                "La efectividad del agrupamiento es aproximadamente %.1f%%. Esto significa que el algoritmo ha podido identificar correctamente la agrupación natural de %.1f%% de los datos.", 
                (100-incorrectPercentage), (100-incorrectPercentage)));
            
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
        
        // Explicación general del algoritmo para usuarios no especializados
        resultJson.put("explicacionGeneral", "El árbol de decisión (J48) es una técnica que analiza los datos para tomar " +
            "decisiones, similar a un diagrama de flujo. Permite predecir resultados basados en características previas.");
        
        // Resumen de precisión simplificado
        double accuracy = 100 * (1 - eval.errorRate());
        resultJson.put("precisión", String.format("El modelo tiene una precisión aproximada del %.1f%%", accuracy));
        resultJson.put("explicacionPrecision", "La precisión indica qué porcentaje de predicciones hace correctamente el modelo. " +
            "Una precisión más alta significa que el modelo es más confiable.");
        
        // Explicación del método de evaluación
        if ("cross-validation".equalsIgnoreCase(evaluationMethod)) {
            resultJson.put("metodoEvaluacion", "Se usó validación cruzada, una técnica que divide los datos en partes para " +
                "asegurar que el modelo funcione bien con datos nuevos.");
        } else {
            resultJson.put("metodoEvaluacion", "Se evaluó el modelo con los mismos datos usados para entrenarlo, lo que puede " +
                "dar una idea de su funcionamiento básico.");
        }
        
        // Información sobre el atributo objetivo
        resultJson.put("atributoObjetivo", "Se está prediciendo: " + targetAttributeName);
        
        // Información detallada por clases
        int numClasses = data.attribute(data.classIndex()).numValues();
        JSONArray clasesInfo = new JSONArray();
        
        for (int i = 0; i < numClasses; i++) {
            String className = data.classAttribute().value(i);
            JSONObject classInfo = new JSONObject();
            classInfo.put("clase", className);
            classInfo.put("precision", eval.precision(i) * 100);
            classInfo.put("exhaustividad", eval.recall(i) * 100);
            classInfo.put("explicacion", String.format(
                "Para la clase '%s', el modelo identifica correctamente el %.1f%% de los casos reales, " +
                "y cuando predice esta clase, acierta el %.1f%% de las veces.", 
                className, eval.recall(i) * 100, eval.precision(i) * 100));
            clasesInfo.put(classInfo);
        }
        resultJson.put("informacionPorClase", clasesInfo);
        
        // Complejidad del árbol (simplificado)
        resultJson.put("complejidadArbol", "El árbol de decisión tiene " + j48.measureTreeSize() + " nodos en total.");
        resultJson.put("reglasGeneradas", "El modelo generó " + j48.measureNumRules() + " reglas de decisión.");
        
        // Mantener datos técnicos para profesionales
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
        
        // Explicación general para personas no especializadas
        resultJson.put("explicacionGeneral", "La Red Neuronal (MLP) es un modelo que imita cómo funciona el cerebro para " +
            "identificar patrones complejos en los datos. Este análisis te permite predecir el valor de '" + 
            targetAttributeName + "' basándose en las otras características.");
        
        // Precisión del modelo explicada de forma sencilla
        double accuracy = 100 - eval.errorRate() * 100;
        resultJson.put("precision", String.format("El modelo tiene una precisión de aproximadamente %.1f%%", accuracy));
        resultJson.put("explicacionPrecision", "La precisión indica qué tan bueno es el modelo haciendo predicciones. " +
            "Una precisión más alta (cercana al 100%) significa que el modelo es más confiable.");
        
        // Explicación sobre el método de evaluación usado
        if ("cross-validation".equalsIgnoreCase(evaluationMethod)) {
            resultJson.put("metodoEvaluacion", "Para evaluar la calidad del modelo, se usó un método llamado 'validación cruzada', " +
                "que divide los datos en varias partes para asegurar que el modelo funciona bien con información nueva.");
        } else {
            resultJson.put("metodoEvaluacion", "El modelo fue evaluado con los mismos datos que se usaron para crearlo. " +
                "Esto da una idea inicial de su funcionamiento, pero podría ser optimista.");
        }
        
        // Descripción estructural de la red en lenguaje sencillo
        int inputLayerSize = data.numAttributes() - 1; // Todos los atributos menos la clase
        int outputLayerSize = data.attribute(data.classIndex()).numValues(); // Número de clases
        int hiddenLayerSize = (inputLayerSize + outputLayerSize) / 2;
        
        resultJson.put("estructuraRed", String.format(
            "La red neuronal analiza %d características de entrada para predecir entre %d posibles resultados de %s, " +
            "utilizando %d neuronas intermedias para procesar la información.", 
            inputLayerSize, outputLayerSize, targetAttributeName, hiddenLayerSize));
        
        // Información sobre cada clase de salida
        JSONArray clasesInfo = new JSONArray();
        for (int i = 0; i < outputLayerSize; i++) {
            JSONObject classInfo = new JSONObject();
            String className = data.classAttribute().value(i);
            
            classInfo.put("clase", className);
            classInfo.put("precision", eval.precision(i) * 100);
            classInfo.put("exhaustividad", eval.recall(i) * 100);
            
            classInfo.put("explicacion", String.format(
                "Para '%s', el modelo identifica correctamente el %.1f%% de los casos reales, y " +
                "cuando predice esta categoría, acierta el %.1f%% de las veces.",
                className, eval.recall(i) * 100, eval.precision(i) * 100));
            
            clasesInfo.put(classInfo);
        }
        resultJson.put("resultadosPorCategoria", clasesInfo);
        
        // Tiempo de entrenamiento en formato amigable
        resultJson.put("tiempoEntrenamiento", "El modelo se entrenó durante 500 ciclos para aprender los patrones en los datos.");
        
        // Complejidad del modelo explicada
        resultJson.put("complejidadModelo", String.format(
            "Este modelo tiene una estructura de 3 capas con %d entradas, %d neuronas intermedias y %d salidas.", 
            inputLayerSize, hiddenLayerSize, outputLayerSize));
        
        // Mantener la información técnica para usuarios avanzados
        resultJson.put("resumenEvaluacion", eval.toSummaryString("", false));
        resultJson.put("precisionPorClase", eval.toClassDetailsString());
        resultJson.put("matrizConfusion", eval.toMatrixString());
        
        // Datos para la visualización de la red neuronal
        JSONObject networkJson = new JSONObject();
        networkJson.put("inputLayer", inputLayerSize);
        networkJson.put("hiddenLayers", new int[]{hiddenLayerSize});
        networkJson.put("outputLayer", outputLayerSize);
        networkJson.put("learningRate", 0.3);
        networkJson.put("momentum", 0.2);
        networkJson.put("trainingTime", 500);
        networkJson.put("accuracy", accuracy);
        
        resultJson.put("networkStructure", networkJson);

        return resultJson.toString(2); // Indentado bonito
    } catch (Exception e) {
        e.printStackTrace();
        return "{\"error\":\"Error al realizar la clasificación con MLP: " + e.getMessage() + "\"}";
    }
}
}

