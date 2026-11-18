plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "io.github.aubergineux"
version = providers.gradleProperty("version").getOrElse("0.1.0")
description = "PvP practice plugin for Paper: duels, kits, ranked queues, arenas and stats."

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.255-stable")
    implementation("com.zaxxer:HikariCP:7.1.0") {
        exclude(group = "org.slf4j")
    }

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.current())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("Riposte-${project.version}.jar")
    relocate("com.zaxxer.hikari", "io.github.aubergineux.riposte.libs.hikari")
}

tasks.jar { enabled = false }
tasks.build { dependsOn(tasks.shadowJar) }
