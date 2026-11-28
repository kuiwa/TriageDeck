pluginManagement {
    repositories {
        // 优先 Maven Central：Spring Boot Gradle 插件 marker 已发布在此，
        // 避免容器/内网环境无法访问 plugins.gradle.org 时构建失败
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "triagedeck"

include("platform")
include("security")
include("alert")
include("audit")
include("ai")
include("notify")
include("app")

include(":device-simulator")
project(":device-simulator").projectDir = file("tools/device-simulator")
