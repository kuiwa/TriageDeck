import org.gradle.api.tasks.testing.Test

plugins {
    id("java-library")
}

group = "com.triagedeck"
version = "0.1.0"

dependencies {
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.data.jpa)
    implementation(project(":platform"))
    implementation(project(":security"))
    implementation(project(":alert"))
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
