plugins {
    `kotlin-dsl`
    id("org.ajoberstar.grgit") version "4.1.1"

}
dependencies {

    implementation("org.ajoberstar.grgit:grgit-core:4.1.1")
}

repositories {
    mavenCentral()
}
