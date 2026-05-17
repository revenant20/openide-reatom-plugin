rootProject.name = "openide-reatom-plugin"

include(":ide-plugin", ":ts-plugin")
project(":ide-plugin").projectDir = file("packages/ide-plugin")
project(":ts-plugin").projectDir = file("packages/ts-plugin")
