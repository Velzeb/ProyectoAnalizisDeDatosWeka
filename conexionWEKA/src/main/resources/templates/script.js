document.addEventListener('DOMContentLoaded', function() {
    toggleEvaluationOptions();
    toggleTargetAttributeVisibility();

    // Mostrar nombre del archivo seleccionado
    const fileInput = document.getElementById('fileInput');
    const fileNameDisplay = document.getElementById('fileNameAnalysis');
    
    fileInput.addEventListener('change', function() {
        if (this.files && this.files.length > 0) {
            fileNameDisplay.textContent = this.files[0].name;
            // Obtener atributos del archivo
            uploadFileForAttributes(this.files[0]);
        } else {
            fileNameDisplay.textContent = 'Ningún archivo seleccionado';
            clearTargetAttributeOptions();
            document.getElementById('targetAttributeGroup').classList.add('hidden');
        }
    });
    
    // Inicializar Viz.js para visualización de árboles
    window.viz = new Viz();
});

function toggleEvaluationOptions() {
    const method = document.getElementById('method').value;
    const evaluationOptions = document.getElementById('evaluationOptions');
    const numClustersDiv = document.getElementById('numClustersDiv');
    
    evaluationOptions.classList.add('hidden');
    numClustersDiv.classList.add('hidden');
    
    if (method === "classificationj48") {
        evaluationOptions.classList.remove('hidden');
    } else if (method === "kmeans") {
        numClustersDiv.classList.remove('hidden');
    }
}

function toggleTargetAttributeVisibility() {
    const method = document.getElementById('method').value;
    const targetAttributeGroup = document.getElementById('targetAttributeGroup');
    if (method === "classificationj48" || method === "mlp" || method === "kmeans") {
        targetAttributeGroup.classList.remove('hidden');
    } else {
        targetAttributeGroup.classList.add('hidden');
    }
}

// Esta función se define ahora en index.html y no necesitamos duplicarla aquí
// La dejamos en comentarios como referencia
/*
async function uploadFile() {
    // Implementación movida a index.html
}
*/

async function uploadFileForAttributes(file) {
    const formData = new FormData();
    formData.append("file", file);

    try {
        const response = await fetch("http://localhost:8080/api/upload/attributes", {
            method: "POST",
            body: formData
        });

        if (response.ok) {
            const result = await response.json();
            if (result.attributes && Array.isArray(result.attributes)) {
                populateTargetAttributeOptions(result.attributes);
                document.getElementById('targetAttributeGroup').classList.remove('hidden');
            } else {
                console.error("No se recibieron atributos o el formato es incorrecto:", result);
                clearTargetAttributeOptions();
                document.getElementById('targetAttributeGroup').classList.add('hidden');
            }
        } else {
            console.error("Error al obtener los atributos:", response.statusText);
            clearTargetAttributeOptions();
            document.getElementById('targetAttributeGroup').classList.add('hidden');
        }
    } catch (error) {
        console.error("Error al comunicar con el servidor para obtener atributos:", error);
        clearTargetAttributeOptions();
        document.getElementById('targetAttributeGroup').classList.add('hidden');
    }
}

function populateTargetAttributeOptions(attributes) {
    const targetAttributeSelect = document.getElementById('targetAttribute');
    targetAttributeSelect.innerHTML = '<option value="">Selecciona un atributo</option>';
    attributes.forEach(attribute => {
        const option = document.createElement('option');
        option.value = attribute;
        option.textContent = attribute;
        targetAttributeSelect.appendChild(option);
    });
}

function clearTargetAttributeOptions() {
    const targetAttributeSelect = document.getElementById('targetAttribute');
    targetAttributeSelect.innerHTML = '<option value="">Selecciona un atributo</option>';
}

// Estas funciones se han movido al index.html
// Las dejamos en comentarios como referencia
/*
function formatResultsForDisplay(jsonResult, method) {
    // Implementación movida a index.html
}

function renderVisualization(jsonResult, method) {
    // Implementación movida a index.html
}

function renderJ48Tree(dotGraph) {
    // Implementación movida a index.html
}

function renderKMeansClusters(kmeansData) {
    // Implementación movida a index.html
}

function renderNeuralNetwork(networkStructure) {
    // Implementación movida a index.html
}

function generateClusterColors(numClusters) {
    // Implementación movida a index.html
}

function getAccuracyColor(accuracy) {
    // Implementación movida a index.html
}
*/