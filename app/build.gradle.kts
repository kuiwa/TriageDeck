plugins {
    id("org.springframework.boot")
}

apply(plugin = "java")

group = "io.github.wangkui.triagedeck"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.validation)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.spring.kafka)
    testImplementation(libs.spring.boot.test)
}

tasks.named<bootJar>("bootJar") {
    launchScript()
}
