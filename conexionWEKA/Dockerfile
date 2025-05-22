FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# Crear un directorio temporal para archivos de WEKA
RUN mkdir -p /tmp/weka-files && chmod 777 /tmp/weka-files

# Instalar dependencias del sistema
RUN apk add --no-cache bash

# Copiar archivos Maven
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:resolve

# Copiar el código fuente
COPY src ./src
RUN chmod +x mvnw
RUN ./mvnw package -DskipTests

# Configuración de la JVM para rendimiento en containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=80.0"

# Exponer el puerto
EXPOSE 8080

# Ejecutar la aplicación
CMD java $JAVA_OPTS -jar target/demo-0.0.1-SNAPSHOT.jar
