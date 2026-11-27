rootProject.name = "triagedeck"

include("platform")
include("security")
include("alert")
include("audit")
include("ai")
include("notify")
include("app")

include(":device-simulator")
project(":device-simulator").projectDir = file("tools/device-simulator")
