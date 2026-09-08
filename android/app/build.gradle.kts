import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.rsm.eztrivia"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rsm.eztrivia"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0-alpha01"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // AGP 9.4 rejects Provider values in the legacy SourceSet API. This is a
    // concrete generated directory, with task ordering carried explicitly by
    // preBuild below.
    sourceSets["main"].assets.srcDir(file("$buildDir/generated/questionCatalog"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val generatedAssetDir = file("$buildDir/generated/questionCatalog")
val generatedCatalog = layout.buildDirectory.file("generated/questionCatalog/questions.json")
val repositoryRoot = rootProject.projectDir.parentFile

val generateQuestionCatalog = tasks.register<Exec>("generateQuestionCatalog") {
    val source = repositoryRoot.resolve("QuestionReview.csv")
    val exporter = repositoryRoot.resolve("Scripts/export_android_catalog.py")

    inputs.file(source)
    inputs.file(exporter)
    outputs.file(generatedCatalog)

    commandLine(
        "python3",
        exporter.absolutePath,
        "--input", source.absolutePath,
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

tasks.named("preBuild") {
    dependsOn(generateQuestionCatalog, generateFlagAssets)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
