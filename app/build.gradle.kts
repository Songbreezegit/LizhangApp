import java.security.MessageDigest
import java.time.LocalDate
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
val legalApprovalFile = rootProject.file("release/legal-approval.properties")

fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

fun legalContentInputs(): List<java.io.File> = (
    legalAssetDirectory.walkTopDown().filter { it.isFile }.toList() +
        file("src/main/res").walkTopDown().filter {
            it.isFile && it.parentFile.name.startsWith("values") &&
                it.extension == "xml" && (it.name.startsWith("legal") || it.name.startsWith("privacy"))
        }.toList()
    ).sortedBy { it.relativeTo(projectDir).invariantSeparatorsPath }

// 逐文件摘要加上相对路径后再次取摘要，新增、删除、改名或任一翻译变化都会使批准失效。
fun legalContentDigest(): String = sha256(legalContentInputs().joinToString("") {
    "${it.relativeTo(projectDir).invariantSeparatorsPath}\n${sha256(it.readBytes())}\n"
}.toByteArray(Charsets.UTF_8))

fun readLegalProperties(source: java.io.File): Properties {
    if (!source.isFile) throw GradleException("缺少发布批准资料：${source.relativeTo(rootDir)}")
    return Properties().apply { source.reader(Charsets.UTF_8).use { load(it) } }
}

// 七语言使用同一待批准标记规则；扫描脚本直接读取此规则，避免只清理中文后误发其他草稿。
val unapprovedLegalText = Regex(
    rootProject.file("release/unapproved-content-pattern.txt").readText(Charsets.UTF_8).trim(),
    RegexOption.IGNORE_CASE
)

val verifyOfficialReleaseContent by tasks.registering {
    group = "verification"
    description = "拒绝未经本人批准的正式发布：检查正文、七语言告知、元数据、摘要及签名身份确认。"
    doLast {
        val approval = readLegalProperties(legalApprovalFile)
        val metadata = readLegalProperties(legalAssetDirectory.resolve("metadata.properties"))
        fun approvedValue(key: String): String {
            val value = approval.getProperty(key)?.trim().orEmpty()
            if (value.isEmpty() || unapprovedLegalText.containsMatchIn(value)) {
                throw GradleException("正式发布被阻止：$key 尚未由开发者本人确认。")
            }
            return value
        }
        if (approvedValue("approval_status") != "approved" || metadata.getProperty("approval_status") != "approved") {
            throw GradleException("正式发布被阻止：正文和发布批准文件必须同时明确标记 approved。")
        }
        listOf("operator_name", "contact_email", "effective_date", "policy_version", "filing_record", "reviewed_by").forEach { key ->
            if (approvedValue(key) != metadata.getProperty(key)?.trim()) {
                throw GradleException("正式发布被阻止：批准资料与离线正文元数据的 $key 不一致。")
            }
        }
        if (!approvedValue("contact_email").matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
            throw GradleException("正式发布被阻止：联系邮箱格式不完整。")
        }
        listOf("effective_date", "approved_at").forEach { key ->
            try { LocalDate.parse(approvedValue(key)) } catch (_: Exception) {
                throw GradleException("正式发布被阻止：$key 必须是本人确认的 YYYY-MM-DD 日期。")
            }
        }
        if (approvedValue("filing_status") != "confirmed" || approvedValue("signing_identity_status") != "confirmed") {
            throw GradleException("正式发布被阻止：备案资料和既有正式签名身份均须本人明确确认。")
        }
        if (!approvedValue("signing_certificate_sha256").matches(Regex("^[a-fA-F0-9]{64}$"))) {
            throw GradleException("正式发布被阻止：须填写既有正式签名证书的 SHA-256 指纹，不得生成替代密钥。")
        }
        val inputs = legalContentInputs()
        listOf("privacy.md", "terms.md", "help.md").forEach { name ->
            if (!legalAssetDirectory.resolve(name).isFile) throw GradleException("正式发布被阻止：缺少完整离线正文 $name。")
        }
        listOf("values", "values-b+zh+Hant", "values-en", "values-ja", "values-ko", "values-es", "values-fr").forEach { locale ->
            listOf("privacy_notice.xml", "legal_strings.xml").forEach { name ->
                if (!file("src/main/res/$locale/$name").isFile) {
                    throw GradleException("正式发布被阻止：缺少 $locale/$name 告知或回退文案。")
                }
            }
        }
        inputs.forEach { source ->
            if (unapprovedLegalText.containsMatchIn(source.readText(Charsets.UTF_8))) {
                throw GradleException("正式发布被阻止：${source.relativeTo(projectDir)} 仍包含待确认或未批准的文案。")
            }
        }
        val policySource = file("src/main/java/com/yangsong/lizhang/domain/legal/LegalDocument.kt")
        if (!policySource.isFile) throw GradleException("正式发布被阻止：无法核对隐私告知版本常量。")
        val currentVersion = Regex("CURRENT_VERSION\\s*=\\s*\"([^\"]+)\"").find(policySource.readText())?.groupValues?.get(1)
        if (currentVersion != approvedValue("policy_version")) {
            throw GradleException("正式发布被阻止：告知版本常量与批准的政策版本不一致。")
        }
        if (approvedValue("approved_content_sha256") != legalContentDigest()) {
            throw GradleException("正式发布被阻止：正文、元数据或任一语言告知已改变，需本人重新审阅并批准当前摘要。")
        }
        logger.lifecycle("正式发布内容批准检查通过；该结果不代表 APK 已完成正式签名或商店批准。")
    }
}

tasks.register("printLegalContentDigest") {
    group = "verification"
    description = "输出待本人审阅的法律正文、元数据及七语言告知内容摘要，不表示批准。"
    doLast { logger.lifecycle("legal_content_sha256=${legalContentDigest()}") }
}

android {
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
    namespace = "com.yangsong.lizhang"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yangsong.lizhang"
        minSdk = 26
        targetSdk = 36
        versionCode = 23
        versionName = if (officialRelease) "1.0.0" else "1.0.0-rc2"
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
        include("strings.xml", "legal_strings.xml", "privacy_notice.xml")
    }
    into(layout.buildDirectory.dir("generated/chineseResources/values-zh"))
}
android.sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("generated/chineseResources"))
tasks.named("preBuild").configure {
    dependsOn(generateChineseResources)
    if (officialRelease) dependsOn(verifyOfficialReleaseContent)
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
