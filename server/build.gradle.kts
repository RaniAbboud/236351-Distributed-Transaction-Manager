plugins {
    application
    kotlin("jvm")// version "1.5.30"
    idea

    // val kotlinVersion = "1.4.31"
    id("org.springframework.boot") version "2.4.4"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    // kotlin-spring is a wrapper on top of all-open - https://kotlinlang.org/docs/all-open-plugin.html#spring-support
    kotlin("plugin.spring") version "1.5.30" //  version kotlinVersion
    // kotlin-jpa is wrapped on top of no-arg - https://kotlinlang.org/docs/no-arg-plugin.html#jpa-support
    kotlin("plugin.jpa") version "1.5.30" // version kotlinVersion
}

java.sourceCompatibility = JavaVersion.VERSION_11

dependencies {
    // Protobuf Dependencies
    implementation(project(":stub"))

    // gRPC Dependencies
    api("io.grpc:grpc-netty:${rootProject.ext["grpcVersion"]}")
    api("io.grpc:grpc-protobuf:${rootProject.ext["grpcVersion"]}")
    api("com.google.protobuf:protobuf-java-util:${rootProject.ext["protobufVersion"]}")
    api("com.google.protobuf:protobuf-kotlin:${rootProject.ext["protobufVersion"]}")
    api("io.grpc:grpc-kotlin-stub:${rootProject.ext["grpcKotlinVersion"]}")

    //     https://mvnrepository.com/artifact/org.slf4j/slf4j-log4j12
    // implementation("org.slf4j:slf4j-log4j12:${rootProject.ext["log4jVersion"]}")

    // https://mvnrepository.com/artifact/org.apache.zookeeper/zookeeper
    implementation("org.apache.zookeeper:zookeeper:${rootProject.ext["zookeeperVersion"]}")

    // https://github.com/MicroUtils/kotlin-logging
    //    implementation("io.github.microutils:kotlin-logging-jvm:2.0.10")
    //    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.5.2")

    // Coroutine dependencies
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${rootProject.ext["coroutinesVersion"]}")

    // Spring (REST API) Dependencies
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}
