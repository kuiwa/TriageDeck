import org.gradle.api.tasks.compile.JavaCompile

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

    // Spring MVC 需要参数名反射（@RequestParam/@PathVariable 省略 name 时）
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-parameters")
    }
}
