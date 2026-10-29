import java.security.MessageDigest
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
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

// APP 备案独立于网站备案；只把本人已核对并批准的真实编号写入安装包。
val appFilingProperties = Properties().apply {
    try {
        providers.fileContents(rootProject.layout.projectDirectory.file("release/app-filing.properties"))
            .asText.get().reader().use { load(it) }
    } catch (_: Exception) {
        throw GradleException("APP 备案配置无法读取，请核对 release/app-filing.properties。")
    }
}
val appFilingStatus = appFilingProperties.getProperty("app_filing_status").orEmpty()
val appFilingDeveloperConfirmed = appFilingProperties.getProperty("app_filing_developer_confirmed", "false")
val appFilingNumber = appFilingProperties.getProperty("app_filing_number").orEmpty()
if (appFilingProperties.stringPropertyNames().any {
        it !in setOf("app_filing_status", "app_filing_number", "app_filing_developer_confirmed")
    } || appFilingDeveloperConfirmed !in setOf("true", "false")) {
    throw GradleException("APP 备案配置包含未知字段或无效的本人确认状态。")
}
val publishedAppFilingNumber = when (appFilingStatus) {
    "pending" -> ""
    "approved" -> {
        if (appFilingDeveloperConfirmed != "true" || appFilingNumber.isBlank()) {
            throw GradleException("APP 备案展示被阻止：须由开发者本人核对真实核发编号并明确确认。")
        }
        if (appFilingNumber != appFilingNumber.trim() || appFilingNumber.any { Character.isISOControl(it) }) {
            throw GradleException("APP 备案展示被阻止：编号须为逐字核对的单行原文，不得包含首尾空白或控制字符。")
        }
        appFilingNumber
    }
    else -> throw GradleException("APP 备案状态仅允许 pending 或 approved，不得推测审批结果。")
}

// 在接触本机签名资料前拒绝配置缓存；普通构建完全不读取签名文件或签名环境变量。
if (officialRelease && gradle.startParameter.isConfigurationCacheRequested) {
    throw GradleException("正式签名禁止配置缓存，请使用 --no-configuration-cache --no-daemon。")
}
val signingPropertiesPath = if (officialRelease) providers.environmentVariable("LIZHANG_SIGNING_PROPERTIES").orNull else null
val signingPropertiesFile = try {
    signingPropertiesPath?.let { rootProject.file(it) }
        ?: rootProject.file("release-signing.local.properties")
} catch (_: Exception) {
    throw GradleException("正式签名被阻止：本机签名配置路径无效，请在本机核对。")
}
val signingProperties = Properties().apply {
    if (officialRelease) {
        try {
            if (signingPropertiesPath != null && !signingPropertiesFile.isFile) {
                throw IllegalArgumentException()
            }
            if (signingPropertiesFile.isFile) signingPropertiesFile.reader(Charsets.UTF_8).use { load(it) }
        } catch (_: Exception) {
            // 不附带底层异常，避免无效路径或配置内容进入日志。
            throw GradleException("正式签名被阻止：本机签名配置不存在、无法读取或格式无效。")
        }
        if (stringPropertyNames().any { it !in setOf("storeFile", "keyAlias", "storeType") }) {
            throw GradleException("正式签名被阻止：本机配置仅允许路径、别名和类型；不得保存密码或其他字段。")
        }
    }
}
fun signingSetting(environment: String, property: String): String? =
    if (officialRelease) {
        providers.environmentVariable(environment).orNull?.takeIf { it.isNotBlank() }
            ?: signingProperties.getProperty(property)?.trim()?.takeIf { it.isNotBlank() }
    } else null

val releaseStorePath = signingSetting("LIZHANG_SIGNING_STORE_FILE", "storeFile")
val releaseKeyAlias = signingSetting("LIZHANG_SIGNING_KEY_ALIAS", "keyAlias")
val releaseStoreType = signingSetting("LIZHANG_SIGNING_STORE_TYPE", "storeType")
val releaseStoreFile = releaseStorePath?.let { path ->
    try { rootProject.file(path).canonicalFile } catch (_: Exception) {
        throw GradleException("正式签名被阻止：密钥库路径无效，请在本机核对。")
    }
}
// 普通 Debug / 未签名候选配置不读取签名密码。
val releaseStorePassword = if (officialRelease) providers.environmentVariable("LIZHANG_SIGNING_STORE_PASSWORD").orNull else null
val releaseKeyPassword = if (officialRelease) providers.environmentVariable("LIZHANG_SIGNING_KEY_PASSWORD").orNull else null

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

fun validLegalDate(value: String): Boolean = value.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) &&
    runCatching { LocalDate.parse(value).toString() == value }.getOrDefault(false)

// 构建阶段仅展开公开字段；内部批准状态和同意记录版本不进入正文。
fun renderLegalMarkdown(source: java.io.File, metadata: Properties): String {
    var rendered = source.readText(Charsets.UTF_8)
    val effectiveDate = metadata.getProperty("effective_date").orEmpty()
    if (!validLegalDate(effectiveDate)) {
        if (officialRelease || effectiveDate != "pending") {
            throw GradleException("法律文档生成被阻止：须由用户确认真实 YYYY-MM-DD 生效日期。")
        }
        // 仅审核包省略日期行，不把 pending、模板或审批说明显示给用户。
        rendered = rendered.lineSequence().filterNot {
            it.trim() == "生效日期：{{effective_date}}"
        }.joinToString("\n")
    }
    listOf("app_name", "operator_type", "operator_name", "contact_email", "effective_date",
        "formal_policy_version", "formal_terms_version", "website_domain").forEach { key ->
        rendered = rendered.replace("{{${key}}}", metadata.getProperty(key))
    }
    if (rendered.isBlank() || rendered.contains("{{") || rendered.contains("}}")) {
        throw GradleException("离线法律文档生成失败：${source.name} 有空白正文或未解析字段。")
    }
    return rendered
}

val generatedOfflineLegalAssets = layout.buildDirectory.dir("generated/offlineLegalAssets")
val generateOfflineLegalAssets by tasks.registering(Sync::class) {
    // 许可等既有资产原样复制；法律区只打包已展开正文，原始模板和内部元数据不进入 APK。
    from("src/main/assets") { exclude("legal/*.md", "legal/metadata.properties") }
    into(generatedOfflineLegalAssets)
    inputs.dir(legalAssetDirectory)
    inputs.file(legalApprovalFile)
    inputs.file("src/main/java/com/yangsong/lizhang/domain/legal/LegalDocument.kt")
    inputs.property("officialRelease", officialRelease)
    doLast {
        val metadata = readLegalProperties(legalAssetDirectory.resolve("metadata.properties"))
        val approval = readLegalProperties(legalApprovalFile)
        val version = Regex("CURRENT_VERSION\\s*=\\s*\"([^\"]+)\"").find(
            file("src/main/java/com/yangsong/lizhang/domain/legal/LegalDocument.kt").readText(Charsets.UTF_8)
        )?.groupValues?.get(1)
        if (version.isNullOrBlank() || metadata.getProperty("policy_version") != version ||
            approval.getProperty("policy_version") != version) {
            throw GradleException("离线法律文档生成失败：常量、元数据和批准资料版本不一致。")
        }
        val target = generatedOfflineLegalAssets.get().asFile.resolve("legal").apply { mkdirs() }
        listOf("privacy.md", "terms.md", "help.md").forEach { name ->
            val source = legalAssetDirectory.resolve(name)
            val publicVersion = when (name) {
                "privacy.md" -> metadata.getProperty("formal_policy_version")
                "terms.md" -> metadata.getProperty("formal_terms_version")
                else -> null
            }
            if (publicVersion != null && !source.readText(Charsets.UTF_8).contains("版本：$publicVersion")) {
                throw GradleException("离线法律文档生成失败：$name 与公开文档版本不一致。")
            }
            target.resolve(name).writeText(renderLegalMarkdown(source, metadata), Charsets.UTF_8)
        }
    }
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
            if (!validLegalDate(approvedValue(key))) {
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
        if (metadata.getProperty("operator_details_status") != "confirmed") {
            throw GradleException("正式发布被阻止：运营资料须确认。")
        }
        // Markdown 正文通过同一元数据填入日期和公开资料；未知字段不能变成正式空白正文。
        listOf("privacy.md", "terms.md", "help.md").forEach { name ->
            val rendered = renderLegalMarkdown(legalAssetDirectory.resolve(name), metadata)
            if (unapprovedLegalText.containsMatchIn(rendered)) {
                throw GradleException("正式发布被阻止：$name 仍有未解析或未批准资料。")
            }
        }
        if (approvedValue("approved_content_sha256") != legalContentDigest()) {
            throw GradleException("正式发布被阻止：正文、元数据或任一语言告知已改变，需本人重新审阅并批准当前摘要。")
        }
        logger.lifecycle("正式发布内容批准检查通过；该结果不代表 APK 已完成正式签名或商店批准。")
    }
}

// 只读取公开证书进行身份比对，不获取私钥；私钥仅交给 Android 签名工具使用。
val verifyReleaseSigning by tasks.registering {
    group = "verification"
    description = "检查正式签名配置及公开证书与本人批准指纹的一致性，不输出密码。"
    dependsOn(verifyOfficialReleaseContent)
    doLast {
        if (!officialRelease) throw GradleException("正式签名检查仅用于 officialRelease=true。")
        val store = releaseStoreFile
        val alias = releaseKeyAlias
        val storePassword = releaseStorePassword
        val keyPassword = releaseKeyPassword
        if (store == null || alias.isNullOrBlank() ||
            storePassword.isNullOrEmpty() || keyPassword.isNullOrEmpty()) {
            throw GradleException("正式签名被阻止：缺少路径、别名或进程内签名密码；不会使用 Debug 密钥。")
        }
        val insideRepository = generateSequence(store.parentFile) { it.parentFile }
            .any { it.resolve(".git").exists() }
        if (!store.isFile || store.toPath().startsWith(rootDir.canonicalFile.toPath()) || insideRepository) {
            throw GradleException("正式签名被阻止：密钥库必须位于 Git 仓库之外的本机私有目录。")
        }
        if (store.name.equals("debug.keystore", ignoreCase = true) ||
            alias.equals("androiddebugkey", ignoreCase = true)) {
            throw GradleException("正式签名被阻止：禁止使用 Android 默认 Debug 密钥。")
        }
        val password = storePassword.toCharArray()
        val certificate = try {
            val keyStore = if (releaseStoreType == null) {
                KeyStore.getInstance(store, password)
            } else {
                KeyStore.getInstance(releaseStoreType).apply { store.inputStream().use { load(it, password) } }
            }
            if (!keyStore.isKeyEntry(alias)) {
                throw GradleException("正式签名被阻止：所选别名没有签名密钥条目。")
            }
            keyStore.getCertificate(alias) as? X509Certificate
                ?: throw GradleException("正式签名被阻止：所选别名缺少 X.509 公开证书。")
        } catch (_: Exception) {
            // 不拼接底层异常，防止配置值或凭据出现在日志中。
            throw GradleException("正式签名被阻止：无法读取所选公开证书，请在本机核对配置与密码。")
        } finally { password.fill('\u0000') }
        val rsaKey = certificate.publicKey as? RSAPublicKey
            ?: throw GradleException("正式签名被阻止：APP 备案签名身份须使用 RSA 公钥。")
        if (rsaKey.modulus.bitLength() < 2048 ||
            certificate.subjectX500Principal.name.contains("CN=Android Debug", ignoreCase = true)) {
            throw GradleException("正式签名被阻止：证书为 Debug 身份或 RSA 密钥长度不足。")
        }
        try { certificate.checkValidity() } catch (_: Exception) {
            throw GradleException("正式签名被阻止：证书尚未生效或已过期。")
        }
        val approved = readLegalProperties(legalApprovalFile).getProperty("signing_certificate_sha256")
        if (!sha256(certificate.encoded).equals(approved, ignoreCase = true)) {
            throw GradleException("正式签名被阻止：实际公开证书与本人批准的 SHA-256 指纹不一致。")
        }
        logger.lifecycle("正式签名公开证书身份比对通过；尚未表示 APK 或商店验收完成。")
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
        versionCode = 25
        versionName = "1.0.0"
        buildConfigField("String", "APP_FILING_NUMBER",
            "\"${publishedAppFilingNumber.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        resourceConfigurations += listOf("zh", "b+zh+Hant", "en", "ja", "ko", "es", "fr")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 常规 Release 保持未签名候选；正式签名只能与现有批准开关一起启用。
    if (officialRelease) {
        signingConfigs {
            create("release") {
                storeFile = releaseStoreFile
                keyAlias = releaseKeyAlias
                storePassword = releaseStorePassword
                keyPassword = releaseKeyPassword
                releaseStoreType?.let { storeType = it }
                enableV1Signing = true
                enableV2Signing = true
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
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
android.sourceSets.getByName("main").assets.setSrcDirs(listOf(generatedOfflineLegalAssets))
tasks.named("preBuild").configure {
    dependsOn(generateChineseResources, generateOfflineLegalAssets)
    if (officialRelease) dependsOn(verifyOfficialReleaseContent, verifyReleaseSigning)
}

// 直接调用打包或签名任务也必须先检查批准和签名身份。
tasks.configureEach {
    if (officialRelease && name in setOf("validateSigningRelease", "packageRelease", "packageReleaseBundle", "signReleaseBundle")) {
        dependsOn(verifyReleaseSigning)
    }
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
