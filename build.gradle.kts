plugins {
    id("org.springframework.boot") version "3.5.0" apply false
}

subprojects {
    apply(plugin = "java-library")

    group = "io.github.wangkui.triagedeck"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}
