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

    // Web & REST
    implementation("io.quarkus:quarkus-resteasy-reactive")
    implementation("io.quarkus:quarkus-resteasy-reactive-jackson")
    implementation("io.quarkus:quarkus-vertx-http")

    // Database
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("io.quarkus:quarkus-flyway")
    implementation("io.quarkus:quarkus-agroal")

    // Redis
    implementation("io.quarkus:quarkus-redis-client")

    // Health & Monitoring
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-container-image-docker")

    // Scheduler
    implementation("io.quarkus:quarkus-scheduler")

    // Security
    implementation("io.quarkus:quarkus-smallrye-jwt")
    implementation("io.quarkus:quarkus-smallrye-jwt-build")
    implementation("io.quarkus:quarkus-security")
    implementation("io.quarkus:quarkus-elytron-security-properties-file")

    // External libraries
    implementation("io.nats:jnats:2.17.6")
    implementation("org.mindrot:jbcrypt:0.4")

    // Core
    implementation("io.quarkus:quarkus-arc")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

quarkus {
    setFinalName("economy-quarkus")
}

// Force Java 21 for compatibility
tasks.withType<JavaCompile> {
    options.release.set(21)
    options.compilerArgs.addAll(listOf(
        "-Xlint:deprecation",
        "-Xlint:unchecked"
    ))
}

// Set JVM arguments for build
tasks.withType<Test> {
    jvmArgs = listOf(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED"
    )
}
