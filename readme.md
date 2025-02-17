# Grade Checker

![Build Status](https://github.com/professor-enoqueleal/grade-checker/actions/workflows/maven.yml/badge.svg)

[Log4J Pattern Layout](https://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/PatternLayout.html)

## Setting environment variables for Linux

```shell
export GITHUB_CLIENT_ID=your-client-id
export GITHUB_CLIENT_SECRET=your-client-secret
export GITHUB_BASE_URL=https://github.com
```

## Setting environment variables for Windows

```shell
set GITHUB_CLIENT_ID=your-client-id
set GITHUB_CLIENT_SECRET=your-client-secret
set GITHUB_BASE_URL=https://github.com
```

## Set execution permissions

To execute commands *maven wrapper*, permissions are necessary. To set permissions for execution, using this command:

```shell
$ chmod +x mvnw
```

---

## Configuração da JVM

Para garantir que todos os módulos não-nomeados possam acessar o pacote `java.util`, adicione o 
seguinte comando à configuração da JVM:

```
--add-opens java.base/java.util=ALL-UNNAMED
```
