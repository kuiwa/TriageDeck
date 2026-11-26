import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("org.springframework.boot")
}

apply(plugin = "java")

group = "com.triagedeck"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.validation)
    implementation(libs.spring.boot.data.jpa)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.spring.kafka)
    implementation(project(":platform"))
    implementation(project(":security"))
    implementation(project(":alert"))
    implementation(project(":audit"))
    implementation(project(":ai"))
    implementation(project(":notify"))
    testImplementation(libs.spring.boot.test)
}

tasks.named<BootJar>("bootJar") {
    launchScript()
}
