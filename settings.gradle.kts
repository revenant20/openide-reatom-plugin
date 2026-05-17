plugins {
    // Auto-provisions the JDK 21 toolchain — downloads it if not installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "openide-reatom-plugin"

include(":ide-plugin", ":ts-plugin")
project(":ide-plugin").projectDir = file("packages/ide-plugin")
project(":ts-plugin").projectDir = file("packages/ts-plugin")
