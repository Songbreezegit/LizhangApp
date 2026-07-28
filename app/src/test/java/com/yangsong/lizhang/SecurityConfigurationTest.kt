package com.yangsong.lizhang

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityConfigurationTest {
    @Test
    fun `源码资源文档中不存在疑似明文令牌`() {
        val project = projectRoot()
        val roots = listOf(File(project, "app/src"), File(project, "docs"), File(project, "README.md"))
        val suspicious = Regex("(?i)(bearer|token)\\s*[:=]\\s*[\"']?[a-f0-9]{32,}")
        val leaked = roots.flatMap { root ->
            if (root.isFile) listOf(root) else root.walkTopDown().filter(File::isFile).toList()
        }.filterNot { it.path.contains("${File.separator}build${File.separator}") }
            .filter { runCatching { suspicious.containsMatchIn(it.readText()) }.getOrDefault(false) }
        assertTrue("发现疑似明文令牌：${leaked.map(File::getName)}", leaked.isEmpty())
    }

    @Test
    fun `localProperties已被Git忽略`() {
        val project = projectRoot()
        assertTrue(File(project, ".gitignore").readText().lineSequence().any { it.trim() == "local.properties" })
        assertFalse(File(project, "app/src/main/assets/local.properties").exists())
    }

    @Test
    fun `隐私数据不会进入系统自动备份或换机迁移`() {
        val project = projectRoot()
        val manifest = File(project, "app/src/main/AndroidManifest.xml").readText()
        val rules = File(project, "app/src/main/res/xml/data_extraction_rules.xml").readText()

        assertTrue(manifest.contains("""android:allowBackup="false""""))
        assertTrue(manifest.contains("""android:fullBackupContent="false""""))
        assertTrue(manifest.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""))
        listOf("database", "sharedpref", "file", "root").forEach { domain ->
            assertTrue("系统备份规则未排除 $domain", rules.contains("""domain="$domain""""))
        }
        assertTrue(rules.contains("<cloud-backup>"))
        assertTrue(rules.contains("<device-transfer>"))
    }

    private fun projectRoot(): File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir")) { "无法读取工作目录" }
        return requireNotNull(File(workingDirectory).parentFile) { "无法定位项目根目录" }
    }
}
