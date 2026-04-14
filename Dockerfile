# Use a imagem base do OpenJDK
FROM openjdk:17-jdk-slim

# Instale o curl
RUN apt-get update && apt-get install -y curl && apt-get clean

# Defina as variáveis de ambiente para o Tomcat
ENV CATALINA_HOME /opt/tomcat
ENV PATH $CATALINA_HOME/bin:$PATH

# Baixe e instale o Tomcat
RUN mkdir -p $CATALINA_HOME \
    && curl -L -O https://dlcdn.apache.org/tomcat/tomcat-10/v10.1.39/bin/apache-tomcat-10.1.39.tar.gz \
    && tar -zxf apache-tomcat-10.1.39.tar.gz --strip-components=1 -C $CATALINA_HOME \
    && rm apache-tomcat-10.1.39.tar.gz \
    && chmod +x $CATALINA_HOME/bin/catalina.sh

# Remova todas as aplicações padrão da pasta webapps
# RUN rm -rf $CATALINA_HOME/webapps/*

# Copie o arquivo WAR da aplicação para o diretório webapps do Tomcat
COPY target/grade-checker.war $CATALINA_HOME/webapps/grade-checker.war

# Copie o arquivo context.xml para o diretório META-INF do WAR
COPY src/main/webapp/META-INF/context.xml $CATALINA_HOME/conf/context.xml
# COPY tomcat/web.xml $CATALINA_HOME/conf/web.xml

# Exponha a porta 8080
EXPOSE 8080

# Comando para iniciar o Tomcat
ENTRYPOINT ["sh", "-c", "$CATALINA_HOME/bin/catalina.sh run"]