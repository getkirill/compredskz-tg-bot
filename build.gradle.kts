plugins {
    kotlin("jvm") version "2.3.10"
    id("com.gradleup.shadow") version "9.4.0"
}

group = "dev.kraskaska"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}
val tgbotapi_version = "32.0.0"
dependencies {
    testImplementation(kotlin("test"))
    implementation("dev.inmo:tgbotapi:$tgbotapi_version")
    implementation("org.postgresql:postgresql:42.7.10")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "dev.kraskaska.compredskz.MainKt"
    }
}