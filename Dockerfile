#Create an Scada-LTS.war file and deploy it into Docker Tomcat Image.
FROM tomcat:9.0.53
LABEL maintainer="rjajko@softq.pl"
COPY WebContent/WEB-INF/lib/mysql-connector-java-5.1.49.jar /usr/local/tomcat9/lib/mysql-connector-java-5.1.49.jar
COPY tomcat/lib/activation.jar /usr/local/tomcat9/lib/activation.jar
COPY tomcat/lib/jaxb-api-2.4.0-b180830.0359.jar /usr/local/tomcat9/lib/jaxb-api-2.4.0-b180830.0359.jar
COPY tomcat/lib/jaxb-core-3.0.2.jar /usr/local/tomcat9/lib/jaxb-core-3.0.2.jar
COPY tomcat/lib/jaxb-runtime-2.4.0-b180830.0438.jar /usr/local/tomcat9/lib/jaxb-runtime-2.4.0-b180830.0438.jar
COPY docker/config/context.xml /usr/local/tomcat9/context.xml
COPY build/libs/ScadaBR.war /usr/local/tomcat9/webapps/

RUN apt update && apt install wait-for-it && apt clean && rm -rf /var/lib/apt/lists/*
