plugins {
    id("java-library")
}

group = "com.triagedeck"
version = "0.1.0"

dependencies {
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.security)
    implementation(libs.spring.boot.data.jpa)
    implementation(libs.spring.kafka)
    testImplementation(libs.spring.boot.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    include("**/*Test.class", "**/*IT.class")
}
