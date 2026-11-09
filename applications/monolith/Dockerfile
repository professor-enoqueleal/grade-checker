# syntax=docker/dockerfile:1.7
#
# Grade Checker (monolith) - Amazon Corretto 17, Maven Wrapper build (tests skipped), Jetty 11 from Maven Central,
# WAR deployed as ROOT, non-root service user (same Jetty setup as grade-checker-ec2.yaml).
#
# The image is built from the LOCAL working tree (not a git clone), so it runs the code being developed.
# grade-checker-core (applications/library-core, the library shared with the serverless version) comes from the build
# context "core": it is compiled and installed first, so no token is needed while the version in pom.xml is the one in
# applications/library-core. Another version is downloaded from GitHub Packages with the optional secret
# maven_settings (settings.xml with a read:packages token), never stored in the image.
#
# From applications/monolith:
#   docker build --build-context core=../library-core -t grade-checker .
#   docker build --build-context core=../library-core --secret id=maven_settings,src=$HOME/.m2/settings.xml -t grade-checker .
#   docker run -d --name grade-checker -p 8080:8080 --env-file .env.local grade-checker

ARG JAVA_IMAGE=amazoncorretto:17-al2023
ARG JETTY_VERSION=11.0.26

# ---------------------------------------------------------------- build: WAR with the Maven Wrapper
FROM ${JAVA_IMAGE} AS build

RUN dnf install -y -q tar gzip which && dnf clean all

WORKDIR /src

# wrapper + pom first: dependency resolution is cached while only the sources change
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY --from=core pom.xml core/pom.xml
COPY --from=core src core/src
COPY src src

# mvnw is committed without the executable bit, so it is run through sh (same as the CloudFormation script)
# grade-checker-core first (into the same cached ~/.m2), then the WAR
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml,required=false \
    sh ./mvnw -B -ntp -DskipTests -f core/pom.xml install \
 && sh ./mvnw -B -ntp -DskipTests package \
 && test -f target/grade-checker.war

# ---------------------------------------------------------------- jetty: download and configure JETTY_BASE
FROM ${JAVA_IMAGE} AS jetty

ARG JETTY_VERSION
RUN dnf install -y -q tar gzip && dnf clean all

RUN curl -fsSL -o /tmp/jetty-home.tar.gz \
      "https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/${JETTY_VERSION}/jetty-home-${JETTY_VERSION}.tar.gz" \
 && tar -xzf /tmp/jetty-home.tar.gz -C /opt \
 && mv "/opt/jetty-home-${JETTY_VERSION}" /opt/jetty-home \
 && rm /tmp/jetty-home.tar.gz

# plus = JNDI used by WEB-INF/jetty-env.xml; jsp + jstl for the pages (same modules as the EC2 setup)
RUN mkdir -p /opt/jetty-base/webapps \
 && cd /opt/jetty-base \
 && java -jar /opt/jetty-home/start.jar --add-modules=server,http,deploy,annotations,plus,jsp,jstl

# ---------------------------------------------------------------- runtime
FROM ${JAVA_IMAGE}

ARG JETTY_VERSION
LABEL org.opencontainers.image.title="grade-checker" \
      org.opencontainers.image.description="Grade Checker on Jetty ${JETTY_VERSION}"

RUN dnf install -y -q shadow-utils && dnf clean all \
 && useradd --system --create-home --home-dir /var/lib/grade-checker --shell /sbin/nologin gradechecker \
 && install -d -o gradechecker -g gradechecker /var/lib/grade-checker/h2db

COPY --from=jetty /opt/jetty-home /opt/jetty-home
COPY --from=jetty --chown=gradechecker:gradechecker /opt/jetty-base /opt/jetty-base
# deployed as ROOT: the pages use absolute links (/home, /detail, ...)
COPY --from=build --chown=gradechecker:gradechecker /src/target/grade-checker.war /opt/jetty-base/webapps/ROOT.war

# db.sql is read from the classpath (inside ROOT.war), so no source file is needed at runtime
WORKDIR /opt/jetty-base

# GitHub OAuth (GITHUB_CLIENT_ID / GITHUB_CLIENT_SECRET) and cache settings are passed with -e at run time
ENV GITHUB_BASE_URL=https://github.com
ENV JAVA_OPTS="-Xms256m -Xmx768m"

USER gradechecker
EXPOSE 8080

# H2 database lives in ~/h2db (/var/lib/grade-checker/h2db) and is kept between starts (db.sql is idempotent);
# mount a volume there so it also survives a new container: -v grade-checker-data:/var/lib/grade-checker/h2db
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
  CMD curl -fsS -o /dev/null http://localhost:8080/login || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /opt/jetty-home/start.jar jetty.home=/opt/jetty-home jetty.base=/opt/jetty-base jetty.http.port=8080"]
