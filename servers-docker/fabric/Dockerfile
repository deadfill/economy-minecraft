FROM eclipse-temurin:21-noble AS buildloader
ARG FABRIC_LOADER_VERSION
RUN apt-get update && apt-get -y install git && \
    rm -rf /var/lib/apt/lists/*
RUN mkdir -p /app
RUN git clone -b $FABRIC_LOADER_VERSION --depth=1 https://github.com/FabricMC/fabric-loader.git /app
ADD https://mirror.gravitlauncher.com/5.6.x/patches/FabricLoader.patch /app/FabricLoader.patch
WORKDIR /app
RUN git apply FabricLoader.patch
RUN ./gradlew build --no-daemon -x test

FROM eclipse-temurin:21-noble AS setup
ARG LAUNCHER_VERSION
ARG FABRIC_LOADER_VERSION
ARG MINECRAFT_VERSION
ARG AUTHLIB_VERSION
RUN mkdir -p /app/data
COPY ServerWrapperConfig.json /app/ServerWrapperConfig.json
ADD https://meta.fabricmc.net/v2/versions/loader/$MINECRAFT_VERSION/$FABRIC_LOADER_VERSION/1.0.1/server/jar /app/server.jar
ADD https://github.com/GravitLauncher/Launcher/releases/download/$LAUNCHER_VERSION/ServerWrapper.jar /app/ServerWrapper.jar
WORKDIR /app
RUN java -jar /app/server.jar nogui
RUN java -jar /app/ServerWrapper.jar installAuthlib https://mirror.gravitlauncher.com/5.6.x/authlib/LauncherAuthlib6.jar
COPY --from=buildloader /app/build/libs/*+local-fat.jar /app/libraries/net/fabricmc/fabric-loader/$FABRIC_LOADER_VERSION/fabric-loader-$FABRIC_LOADER_VERSION.jar
COPY start.sh /app/start.sh
WORKDIR /app/data
VOLUME /app/data
EXPOSE 25565
ENTRYPOINT ["/app/start.sh"]
