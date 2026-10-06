package com.yangsong.lizhang.flow

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.MainActivity
import com.yangsong.lizhang.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 两个方法分别执行，中间由 adb 强制停止进程，验证官方存储的冷启动恢复。 */
class LocalePersistenceInstrumentedTest {
    @Test
    fun setJapaneseForRestart() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("localePersistencePhase") == "setup")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var previous: MainActivity? = null
            scenario.onActivity {
                if (it.resources.configuration.locales[0].language != "ja") previous = it
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ja"))
            }
            awaitJapanese(scenario, previous)
            scenario.onActivity { assertEquals("言語", it.getString(R.string.language)) }
        }
    }

    @Test
    fun verifyJapaneseAfterRestart() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("localePersistencePhase") == "check")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitJapanese(scenario)
            scenario.onActivity {
                assertEquals("ja", AppCompatDelegate.getApplicationLocales().toLanguageTags())
                assertEquals("言語", it.getString(R.string.language))
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            }
        }
    }

    private fun awaitJapanese(scenario: ActivityScenario<MainActivity>, previous: MainActivity? = null) {
        val deadline = android.os.SystemClock.uptimeMillis() + 5000
        var ready = false
        while (!ready && android.os.SystemClock.uptimeMillis() < deadline) {
            // 主线程空闲不代表系统配置已经派发，等待实际目标 Activity 的资源与导航。
            runCatching { scenario.onActivity {
                ready = it !== previous && it.resources.configuration.locales[0].language == "ja" &&
                    it.appearanceHost.isNavigationReady && it.hasWindowFocus()
            } }
            if (!ready) android.os.SystemClock.sleep(30)
        }
        assertTrue("目标日文 Activity 与导航已经就绪", ready)
    }
}
