FROM eclipse-temurin:21-jdk-jammy  
COPY target/product-service-loadtest-0.0.1-SNAPSHOT.jar product-service-loadtest.jar  
ENTRYPOINT ["java","-jar","/product-service-loadtest.jar"]

