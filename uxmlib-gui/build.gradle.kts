plugins {
    id("uxm.library")
}

dependencies {
    api(project(":uxmlib-common"))
    api(project(":uxmlib-item"))
    // api rather than implementation: TextInputInstaller.install and both public TextInput constructors
    // name BedrockDetector and BedrockScreen, so a consumer wiring Bedrock text input has to name them.
    api(project(":uxmlib-bedrock"))
    compileOnly(libs.paper.api)
    compileOnly(libs.bundles.adventure)
}
