plugins {
    id("com.google.protobuf") version "0.8.17" apply false
    kotlin("jvm") version "1.5.31" apply false
    id("org.jlleitschuh.gradle.ktlint") version "10.2.0"
    idea
}

ext["grpcVersion"] = "1.39.0" // need to wait for grpc kotlin to move past this
ext["grpcKotlinVersion"] = "1.2.0" // CURRENT_GRPC_KOTLIN_VERSION
ext["protobufVersion"] = "3.18.1"
ext["coroutinesVersion"] = "1.5.2"
ext["zookeeperVersion"]  = "3.5.9"
ext["log4jVersion"] = "1.7.25"

allprojects {
    repositories {
        mavenLocal()
        jcenter()
        mavenCentral()
        google()
    }

    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "idea")
}

tasks.create("assemble").dependsOn(":server:installDist")
