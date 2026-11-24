plugins {
    kotlin("jvm") version "2.3.20"
}

group = "io.github.Shai0717"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val mockitoAgent = configurations.create("mockitoAgent") { isTransitive = false }

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.254-stable")
    testImplementation("io.papermc.paper:paper-api:26.3.build.254-stable")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.18.0")
    mockitoAgent("org.mockito:mockito-core:5.18.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-javaagent:${mockitoAgent.singleFile}") })
}

tasks.jar {
    archiveBaseName.set("Kolor")
    from("LICENSE")
    doLast {
        check(archiveFile.get().asFile.length() < 100_000) { "Kolor JAR must be smaller than 100 kB" }
    }
}

tasks.register<Jar>("integrationProbeJar") {
    dependsOn(tasks.testClasses)
    archiveClassifier.set("integration-probe")
    from(sourceSets.test.get().output.classesDirs) {
        include("io/github/Shai0717/kolor/IntegrationProbe*.class")
    }
    from("src/test/resources/integration/paper-plugin.yml")
}
