import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
    id("com.android.compose.screenshot")
}

// 常规构建始终是候选包；只有显式开启并通过内容批准检查后才使用正式版本名。
val officialRelease = providers.gradleProperty("officialRelease").map { it == "true" }.getOrElse(false)
val legalAssetDirectory = file("src/main/assets/legal")
val officialVersionCode = providers.gradleProperty("officialVersionCode").orNull?.toIntOrNull()
val legalMetadata = Properties().apply { legalAssetDirectory.resolve("metadata.properties").reader(Charsets.UTF_8).use { load(it) } }

apply(from = rootProject.file("release/legal-release.gradle"))

android {
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
    namespace = "com.yangsong.lizhang"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yangsong.lizhang"
        minSdk = 26
        targetSdk = 36
        versionCode = if (officialRelease) officialVersionCode ?: 23 else 23
        versionName = if (officialRelease) "1.0.0" else "1.0.0-rc4"
        buildConfigField("boolean", "IS_OFFICIAL_RELEASE", officialRelease.toString())
        val policyVersion = if (officialRelease) legalMetadata.getProperty("policy_version") else "1.0.0-rc4-policy-v1"
        require(policyVersion.matches(Regex("[A-Za-z0-9._-]+"))) { "政策版本格式无效" }
        buildConfigField("String", "LEGAL_POLICY_VERSION", "\"$policyVersion\"")
        resourceConfigurations += listOf("zh", "b+zh+Hant", "en", "ja", "ko", "es", "fr")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// 显式中文资源防止「中文 + 英文系统语言」的资源匹配跳过默认中文。
// 简体中文字符串和法律告知均以 values 为维护源，构建时生成限定副本；繁体单独维护。
val generateChineseResources by tasks.registering(Copy::class) {
    from("src/main/res/values") {
        include("strings.xml", "legal_strings.xml", "privacy_notice.xml", "candidate_release_strings.xml")
    }
    into(layout.buildDirectory.dir("generated/chineseResources/values-zh"))
}
android.sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("generated/chineseResources"))
// APK 自带编译时内容摘要，签名前后均拒绝与当前批准内容不一致的旧产物。
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/releaseContentBinding"))
tasks.named("preBuild").configure {
    dependsOn(generateChineseResources, "verifyLegalContentConsistency", "generateReleaseContentBinding")
    if (officialRelease) dependsOn("verifyOfficialReleaseContent")
}

kotlin {
    jvmToolchain(17)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("dev.chrisbanes.haze:haze:1.6.4")

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.room:room-testing:2.7.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
    screenshotTestImplementation("com.android.tools.screenshot:screenshot-validation-api:0.0.1-alpha15")
    screenshotTestImplementation("androidx.compose.ui:ui-tooling")
}
