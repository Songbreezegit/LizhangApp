package com.yangsong.lizhang.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupSessionTest {
    @Test fun 清理任务后新窗口仍可领取启动机会() {
        val session = StartupSession()
        assertTrue(session.claim(restoringState = false, directEntry = false))
        assertTrue(session.claim(restoringState = false, directEntry = false))
    }

    @Test fun 通知窗口跳过但之后独立新窗口仍可播放() {
        val session = StartupSession()
        assertFalse(session.claim(restoringState = false, directEntry = true))
        assertTrue(session.claim(restoringState = false, directEntry = false))
    }

    @Test fun 系统恢复页面不增加启动动画() {
        val session = StartupSession()
        assertFalse(session.claim(restoringState = true, directEntry = false))
        assertTrue(session.claim(restoringState = false, directEntry = false))
    }
}
