import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Firebase setup is intentionally optional for ordinary source CI. Once the
// repository contains a valid google-services.json (or a release workflow
// materializes one before Gradle starts), Crashlytics and its mapping upload
// plugin turn on automatically. Firebase Analytics is deliberately not used.
val firebaseConfigFile = file("google-services.json")
val crashlyticsConfigured = firebaseConfigFile.isFile
if (crashlyticsConfigured) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

val releaseVersionCode = providers.environmentVariable("EZTRIVIA_VERSION_CODE").orNull?.toIntOrNull()
val releaseVersionName = providers.environmentVariable("EZTRIVIA_VERSION_NAME").orNull
val uploadKeystorePath = providers.environmentVariable("ANDROID_UPLOAD_KEYSTORE_PATH")
val uploadKeystorePassword = providers.environmentVariable("ANDROID_UPLOAD_KEYSTORE_PASSWORD")

// Ordinary CI uses Google's official sample IDs so ad/consent code is compiled
// and R8-tested without requiring production credentials. Signed Play releases
// should provide the Android-specific AdMob IDs through these environment vars.
val adMobAppId = providers.environmentVariable("ANDROID_ADMOB_APP_ID").orNull
    ?.takeIf { it.isNotBlank() }
    ?: "ca-app-pub-3940256099942544~3347511713"
val adMobBannerId = providers.environmentVariable("ANDROID_ADMOB_BANNER_ID").orNull
    ?.takeIf { it.isNotBlank() }
    ?: "ca-app-pub-3940256099942544/6300978111"

android {
    namespace = "com.rsm.eztrivia"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rsm.eztrivia"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode ?: 1
        versionName = releaseVersionName ?: "1.0.0-alpha01"

        manifestPlaceholders["adMobAppId"] = adMobAppId
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$adMobBannerId\"")
        buildConfigField("Boolean", "CRASHLYTICS_CONFIGURED", crashlyticsConfigured.toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val uploadSigningConfig = if (uploadKeystorePath.isPresent && uploadKeystorePassword.isPresent) {
        signingConfigs.create("upload") {
            storeFile = file(uploadKeystorePath.get())
            storePassword = uploadKeystorePassword.get()
            keyAlias = "upload"
            keyPassword = uploadKeystorePassword.get()
        }
    } else {
        null
    }

    buildTypes {
        release {
            // Keep ordinary CI unsigned, but sign Play release bundles whenever
            // the protected upload-key environment variables are supplied.
            uploadSigningConfig?.let { signingConfig = it }

            // R8 on release, and exercised by CI: a shrinker configuration that
            // is never built is a configuration that breaks on release day.
            // kotlinx.serialization is reflective enough to need explicit keep
            // rules, which live in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // AGP 9.4 rejects Provider values in the legacy SourceSet API. These are
    // concrete generated directories, with task ordering carried explicitly by
    // preBuild below.
    sourceSets["main"].assets.srcDir(
        layout.buildDirectory.dir("generated/questionCatalog").get().asFile
    )
    sourceSets["main"].res.srcDir(
        layout.buildDirectory.dir("generated/soundResources").get().asFile
    )
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val generatedAssetDir = layout.buildDirectory.dir("generated/questionCatalog").get().asFile
val generatedSoundResDir = layout.buildDirectory.dir("generated/soundResources").get().asFile
val generatedCatalog = layout.buildDirectory.file("generated/questionCatalog/questions.json")
val repositoryRoot = rootProject.projectDir.parentFile

val generateQuestionCatalog = tasks.register<Exec>("generateQuestionCatalog") {
    val source = repositoryRoot.resolve("QuestionReview.csv")
    val flagCatalog = repositoryRoot.resolve("Sources/EZTriviaCore/FlagCatalog.swift")
    val exporter = repositoryRoot.resolve("Scripts/export_android_catalog.py")

    inputs.file(source)
    inputs.file(flagCatalog)
    inputs.file(exporter)
    outputs.file(generatedCatalog)

    commandLine(
        "python3",
        exporter.absolutePath,
        "--input", source.absolutePath,
        "--flag-catalog", flagCatalog.absolutePath,
        "--output", generatedCatalog.get().asFile.absolutePath,
    )
}

val generateFlagAssets = tasks.register<Sync>("generateFlagAssets") {
    val source = repositoryRoot.resolve("EZTriviaApp/Assets.xcassets/Flags")
    inputs.dir(source)
    from(source) {
        include("**/*.imageset/*.png")
        eachFile { path = name }
        includeEmptyDirs = false
    }
    into(generatedAssetDir.resolve("flags"))
}

val generateSoundResources = tasks.register<Sync>("generateSoundResources") {
    val source = repositoryRoot.resolve("EZTriviaApp/Sounds")
    inputs.dir(source)
    from(source) { include("*.wav") }
    into(generatedSoundResDir.resolve("raw"))
}

tasks.named("preBuild") {
    dependsOn(generateQuestionCatalog, generateFlagAssets, generateSoundResources)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("com.google.android.gms:play-services-games-v2:22.0.0")
    implementation("com.google.android.gms:play-services-ads:25.4.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation("com.android.billingclient:billing:9.1.0")

    val firebaseBom = platform("com.google.firebase:firebase-bom:34.19.0")
    implementation(firebaseBom)
    implementation("com.google.firebase:firebase-crashlytics")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
