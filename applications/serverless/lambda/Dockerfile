# syntax=docker/dockerfile:1
# Lambda of the Grade Checker API for serverless/docker-compose.yml (capability serverless-local-compose):
# official AWS base image for Java 21 (with the Runtime Interface Emulator), same handler and /var/task layout as
# `sam build` (template in applications/serverless). Also runs the local database setup (entrypoint override in the compose file).
#
# grade-checker-core (the library shared with the Jetty version) comes from the build context "core" (the folder
# applications/library-core): it is compiled and installed first, so no token is needed while the version of the pom
# here is the version in applications/library-core. Another version is downloaded from GitHub Packages with the optional secret
# maven_settings (a settings.xml with a read:packages token): the secret is never stored in the image.
#   docker compose build api        (compose sets the "core" context and the secret)
#   docker build --build-context core=../../library-core -t grade-checker-api .
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /core
COPY --from=core pom.xml .
COPY --from=core src src
RUN mvn -q -B -DskipTests install
WORKDIR /build
COPY pom.xml .
RUN --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml,required=false \
    mvn -q -B dependency:go-offline
COPY src src
RUN --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml,required=false \
    mvn -q -B -DskipTests package dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/lib

FROM public.ecr.aws/lambda/java:21
COPY --from=build /build/target/classes ${LAMBDA_TASK_ROOT}
COPY --from=build /build/target/lib ${LAMBDA_TASK_ROOT}/lib
CMD ["br.com.gradechecker.lambda.ApplicationHandler::handleRequest"]
