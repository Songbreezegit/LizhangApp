package com.yangsong.lizhang

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityConfigurationTest {
    @Test
    fun `源码资源文档中不存在疑似明文令牌`() {
        val project = File(System.getProperty("user.dir")).parentFile
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
        val project = File(System.getProperty("user.dir")).parentFile
        assertTrue(File(project, ".gitignore").readText().lineSequence().any { it.trim() == "local.properties" })
        assertFalse(File(project, "app/src/main/assets/local.properties").exists())
    }
}
