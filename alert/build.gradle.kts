import org.gradle.api.tasks.testing.Test

plugins {
    id("java-library")
}

group = "com.triagedeck"
version = "0.1.0"

dependencies {
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.validation)
    implementation(libs.spring.boot.data.jpa)
    implementation(project(":platform"))
    testImplementation(libs.spring.boot.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.archunit)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    include("**/*Test.class", "**/*IT.class")
}

sourceSets {
    test {
        resources.srcDir("../app/src/main/resources/db/migration")
    }
}
