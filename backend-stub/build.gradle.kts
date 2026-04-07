plugins {
    application
    java
}

group = "com.huntcore"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    mainClass.set("com.huntcore.backendstub.BackendStubApplication")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}
