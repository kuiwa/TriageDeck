plugins {
    id("java-library")
}

group = "io.github.wangkui.triagedeck"
version = "0.1.0"

dependencies {
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.security)
    implementation(libs.oauth2.resource.server)
    implementation(libs.jcasbin)
    testImplementation(libs.spring.boot.test)
}
