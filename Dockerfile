# Несколько стадий: в образ кладем runtime jlink, а не полный jre
# eclipse-temurin:21-jre-alpine сам по себе около 200 МБ и не проходит лимит 150 МБ
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY src src
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jdk-alpine AS runtime
WORKDIR /opt
COPY --from=build /src/build/libs/sentiment-api.jar /opt/sentiment-api.jar
RUN java -Djarmode=tools -jar /opt/sentiment-api.jar extract --destination /opt/extracted \
    && CP="$(echo /opt/extracted/lib/* | tr ' ' ':')" \
    && DEPS="$(jdeps --ignore-missing-deps --multi-release 21 --print-module-deps \
        --class-path "${CP}" /opt/extracted/sentiment-api.jar)" \
    && echo "jdeps modules: ${DEPS}" \
    && jlink \
        --add-modules "${DEPS},java.logging,java.management,jdk.crypto.ec,jdk.charsets" \
        --strip-debug \
        --no-man-pages \
        --no-header-files \
        --compress=zip-9 \
        --output /opt/jre \
    && rm -rf /opt/jre/legal \
    && rm -f /opt/jre/lib/libawt.so \
             /opt/jre/lib/libawt_headless.so \
             /opt/jre/lib/libawt_xawt.so \
             /opt/jre/lib/libfontmanager.so \
             /opt/jre/lib/libfreetype.so \
             /opt/jre/lib/liblcms.so \
             /opt/jre/lib/libmlib_image.so \
             /opt/jre/lib/libjavajpeg.so \
             /opt/jre/lib/libjsound.so \
             /opt/jre/lib/libsplashscreen.so

FROM alpine:3.24 AS os
RUN apk add --no-cache ca-certificates \
    && adduser -D -u 10001 app \
    && mkdir -p /min/lib /min/etc/ssl/certs \
    && cp -a /lib/ld-musl-*.so.1 /lib/libc.musl-*.so.1 /min/lib/ \
    && cp /etc/ssl/certs/ca-certificates.crt /min/etc/ssl/certs/ \
    && cp /etc/passwd /min/etc/passwd \
    && mkdir -p /min/tmp \
    && chmod 1777 /min/tmp

FROM scratch
COPY --from=os /min/ /
COPY --from=runtime /opt/jre /opt/jre
COPY --from=build /src/build/libs/sentiment-api.jar /app/sentiment-api.jar
ENV PATH="/opt/jre/bin:${PATH}" \
    JAVA_TOOL_OPTIONS="-Djava.awt.headless=true -XX:MaxRAMPercentage=75.0"
USER 10001
EXPOSE 8080
ENTRYPOINT ["/opt/jre/bin/java", "-jar", "/app/sentiment-api.jar"]
