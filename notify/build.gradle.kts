import org.gradle.api.tasks.testing.Test

plugins {
    id("java-library")
}

group = "io.github.wangkui.triagedeck"
version = "0.1.0"

dependencies {
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.data.jpa)
    implementation(libs.spring.kafka)
    implementation(project(":platform"))
    implementation(project(":audit"))
    testImplementation(libs.spring.boot.test)
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
