plugins {
    id("java")
    id("io.quarkus") version "3.6.0"
}

group = "com.example"
version = "0.2.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.6.0"))

    implementation("io.quarkus:quarkus-resteasy-reactive")
    implementation("io.quarkus:quarkus-resteasy-reactive-jackson")
    implementation("io.quarkus:quarkus-vertx-http")

    implementation("io.quarkus:quarkus-hibernate-orm-panache")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-flyway")

    implementation("io.quarkus:quarkus-redis-client")

    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-smallrye-metrics")

    implementation("io.quarkus:quarkus-container-image-docker")

    // ⬇️ вот это нужно
    implementation("io.quarkus:quarkus-scheduler")

    // JWT Security для админ панели
    implementation("io.quarkus:quarkus-smallrye-jwt")
    implementation("io.quarkus:quarkus-smallrye-jwt-build")
    implementation("io.quarkus:quarkus-security")
    implementation("io.quarkus:quarkus-elytron-security-properties-file")

    implementation("io.nats:jnats:2.17.6")
    implementation("org.mindrot:jbcrypt:0.4")

    implementation("io.quarkus:quarkus-arc")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

quarkus {
    setFinalName("economy-quarkus")
}
