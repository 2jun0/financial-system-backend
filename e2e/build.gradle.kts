plugins {
    kotlin("jvm") version "2.3.21"
}

group = "org.financialsystem"
version = "0.0.1-SNAPSHOT"
description = "e2e"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation(platform("tools.jackson:jackson-bom:3.1.7"))
    testRuntimeOnly("org.slf4j:slf4j-simple:1.7.36")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("tools.jackson.module:jackson-module-kotlin")

    testImplementation("io.rest-assured:rest-assured:6.0.1")
    testImplementation("io.rest-assured:kotlin-extensions:6.0.1")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("com.mysql:mysql-connector-j:26.7.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
