#!/bin/bash
# Guía para desplegar en Render.com

# 1. Compilar la aplicación localmente
echo "Compilando la aplicación (puedes saltar este paso)..."
./mvnw clean package -DskipTests

# 2. Confirmar los cambios en Git
echo "Ahora debes confirmar los cambios en Git:"
echo "git add ."
echo "git commit -m 'Preparar para despliegue en Render.com'"
echo "git push origin main"  # Asumiendo que estás usando la rama 'main'

# 3. Instrucciones para crear un servicio web en Render.com
echo ""
echo "=== INSTRUCCIONES PARA DESPLEGAR EN RENDER.COM ==="
echo ""
echo "1. Crear una cuenta en Render.com si aún no la tienes (render.com)"
echo "2. En el dashboard de Render, haz clic en 'New +' y selecciona 'Web Service'"
echo "3. Conecta tu repositorio Git (GitHub, GitLab, etc.)"
echo "4. Configura el servicio:"
echo "   - Nombre: weka-analysis-app (o el que prefieras)"
echo "   - Rama: main (o la rama desde la que despliegas)"
echo "   - Entorno: Docker"
echo "   - Región: La más cercana a tus usuarios"
echo "   - Plan: Free (gratuito)"
echo "5. Haz clic en 'Create Web Service'"
echo ""
echo "Render.com construirá automáticamente la imagen Docker y desplegará la aplicación."
echo "Una vez desplegada, te proporcionará una URL para acceder a la aplicación."
echo ""
echo "Para monitorear el estado de tu aplicación:"
echo "- Accede a /api/health en tu dominio"
echo ""
echo "Si encuentras problemas, revisa los registros (logs) en el dashboard de Render."
echo "=== FIN DE INSTRUCCIONES ==="
