import org.gradle.api.tasks.testing.Test

plugins {
    id("java-library")
}

group = "com.triagedeck"
version = "0.1.0"

dependencies {
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.security)
    implementation(libs.oauth2.resource.server)
    implementation(libs.jcasbin)
    implementation(project(":platform"))
    testImplementation(libs.spring.boot.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
