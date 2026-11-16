plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    alias(libs.plugins.pitest)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

pitest {
    junit5PluginVersion.set(libs.versions.pitestJunit5)
    targetClasses.set(setOf("com.eliaseguizabal.checkout.*"))
    outputFormats.set(setOf("HTML", "XML"))
    timestampedReports.set(false)
    threads.set(4)
}
