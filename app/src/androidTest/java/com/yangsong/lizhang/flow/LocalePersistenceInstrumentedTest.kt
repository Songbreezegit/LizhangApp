package com.yangsong.lizhang.flow

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.yangsong.lizhang.MainActivity
import com.yangsong.lizhang.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** 两个方法分别执行，中间由 adb 强制停止进程，验证官方存储的冷启动恢复。 */
class LocalePersistenceInstrumentedTest {
    @Test
    fun setJapaneseForRestart() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("localePersistencePhase") == "setup")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ja"))
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { assertEquals("言語", it.getString(R.string.language)) }
        }
    }

    @Test
    fun verifyJapaneseAfterRestart() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("localePersistencePhase") == "check")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertEquals("ja", AppCompatDelegate.getApplicationLocales().toLanguageTags())
                assertEquals("言語", it.getString(R.string.language))
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            }
        }
    }
}
