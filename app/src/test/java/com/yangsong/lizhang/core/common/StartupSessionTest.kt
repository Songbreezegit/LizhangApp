package com.yangsong.lizhang.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupSessionTest {
    @Test fun 普通冷启动仅领取一次且热启动不重播() {
        val session = StartupSession()
        assertTrue(session.claim(restoringState = false, directEntry = false))
        assertFalse(session.claim(restoringState = false, directEntry = false))
    }

    @Test fun 通知冷启动直接进入目标且之后不补播() {
        val session = StartupSession()
        assertFalse(session.claim(restoringState = false, directEntry = true))
        assertFalse(session.claim(restoringState = false, directEntry = false))
    }

    @Test fun 系统恢复页面不增加启动动画() {
        val session = StartupSession()
        assertFalse(session.claim(restoringState = true, directEntry = false))
        assertFalse(session.claim(restoringState = false, directEntry = false))
    }
}
