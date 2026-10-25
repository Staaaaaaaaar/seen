plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}
val releaseSigningNames = listOf("SEEN_KEYSTORE_FILE", "SEEN_KEYSTORE_PASSWORD", "SEEN_KEY_ALIAS", "SEEN_KEY_PASSWORD")
val releaseSigning = releaseSigningNames.associateWith { providers.environmentVariable(it).orNull }
require(releaseSigning.values.all { it == null } || releaseSigning.values.all { !it.isNullOrBlank() }) {
    "Release signing requires all four SEEN_KEYSTORE/KEY environment variables"
}
android {
    namespace = "com.seen.film"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.seen.film"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.0.0"
        testInstrumentationRunner = "com.seen.film.SeenTestRunner"
        // External SIGKILL harness is run separately; ordinary connected tests never skip it.
        testInstrumentationRunnerArguments["notClass"] = "com.seen.film.data.BackupCrashTest"
    }
    signingConfigs {
        if (releaseSigning.values.all { !it.isNullOrBlank() }) create("release") {
            storeFile = file(releaseSigning.getValue("SEEN_KEYSTORE_FILE")!!)
            storePassword = releaseSigning.getValue("SEEN_KEYSTORE_PASSWORD")
            keyAlias = releaseSigning.getValue("SEEN_KEY_ALIAS")
            keyPassword = releaseSigning.getValue("SEEN_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
}
kotlin { jvmToolchain(17) }
room { schemaDirectory("$projectDir/schemas") }
dependencyLocking { lockAllConfigurations() }
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.preview)
    debugImplementation(libs.compose.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.core.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.coroutines.android)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    debugImplementation(libs.compose.test.manifest)
}
