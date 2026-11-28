plugins {
    id("application")
}

group = "com.triagedeck"
version = "0.1.0"

dependencies {
    // 工具不参与应用运行，因此不受「不手写 ProducerFactory」约束（该约束针对应用 bean）。
    // spring-boot-starter-web 只为带上 Jackson/日志（kafka-clients 依赖 slf4j）；不引入业务模块。
    implementation(libs.spring.boot.web)
    implementation(libs.spring.kafka)
}

application {
    mainClass.set("com.triagedeck.simulator.DeviceSimulator")
}
