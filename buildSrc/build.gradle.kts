plugins {
    `kotlin-dsl`
    id("java-gradle-plugin")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(gradleApi())
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

gradlePlugin {
    plugins {
        create(PluginCoordinates.ID) {
            id = PluginCoordinates.ID
            implementationClass = PluginCoordinates.IMPLEMENTATION_CLASS
            version = PluginCoordinates.VERSION
        }
    }
}

repositories {
    google()
    mavenCentral()
}

object PluginCoordinates {
    const val ID = "com.wire.plugin"
    const val GROUP = "com.wire.plugin"
    const val VERSION = "0.0.1"
    const val IMPLEMENTATION_CLASS = "com.wire.plugin.WirePlugin"
}
