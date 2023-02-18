plugins {
    application
    id("io.freefair.lombok") version "6.6.2"
}

project.group = "apoos.patcher"
project.version = "1.0.0"

application {
    mainClass.set("apos.patcher.Main")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm-util:9.4")
    implementation("org.ow2.asm:asm-commons:9.4")
    implementation("ch.qos.logback:logback-classic:1.4.5")
    implementation(
        files(
            "src/main/resources/rsclassic.jar"
        )
    )
}

tasks.clean {
    delete(files("${project.rootDir}/out"))
}
