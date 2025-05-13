document.addEventListener('DOMContentLoaded', function() {
    toggleEvaluationOptions();
    
    // Mostrar nombre del archivo seleccionado
    const fileInput = document.getElementById('fileInput');
    const fileNameDisplay = document.getElementById('fileName');
    
    fileInput.addEventListener('change', function() {
        if (this.files && this.files.length > 0) {
            fileNameDisplay.textContent = this.files[0].name;
        } else {
            fileNameDisplay.textContent = 'Ningún archivo seleccionado';
        }
    });
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

async function uploadFile() {
    const fileInput = document.getElementById('fileInput');
    const file = fileInput.files[0];
    if (!file) {
        alert("Por favor, selecciona un archivo antes de continuar");
        return;
    }

    const method = document.getElementById('method').value;
    const evaluation = document.getElementById('evaluation')?.value || "";
    const numClusters = document.getElementById('numClusters')?.value || "";
    
    const resultSection = document.getElementById('result');
    const resultContent = document.getElementById('resultContent');
    
    resultContent.textContent = "Analizando archivo, por favor espera...";
    resultSection.classList.remove('hidden');

    const formData = new FormData();
    formData.append("file", file);
    formData.append("method", method);
    
    if (method === "classificationj48") {
        formData.append("evaluation", evaluation);
    }
    
    if (method === "kmeans") {
        formData.append("numClusters", numClusters);
    }    try {
        const response = await fetch("http://localhost:8080/api/analyze/upload", {
            method: "POST",
            body: formData
        });

        if (response.ok) {
            const result = await response.json();
            
            // Guardar el resultado en localStorage
            const resultId = Date.now().toString();
            localStorage.setItem('analysisResult-' + resultId, JSON.stringify(result));
            localStorage.setItem('lastAnalysisResult', JSON.stringify(result));
            
            // Redirigir a la página de resultados
            window.location.href = '/result?id=' + resultId;
        } else {
            resultContent.innerHTML = "<p>Error en la solicitud: " + response.statusText + "</p>";
        }
    } catch (error) {
        resultContent.innerHTML = "<p>Error al analizar el archivo: " + error.message + "</p>";
    }
}