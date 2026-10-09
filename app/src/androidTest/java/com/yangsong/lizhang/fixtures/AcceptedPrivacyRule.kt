package com.yangsong.lizhang.fixtures

import androidx.test.core.app.ApplicationProvider
import com.yangsong.lizhang.LiZhangApplication
import org.junit.Assert.assertTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * 只用于已同意后的业务、外观及动画测试，在 Activity 启动和首帧采样之前准备前置状态。
 * 不请求通讯录或通知权限，不消费功能引导；首次告知及尚未确认的升级验收禁止使用。
 * 首次安装验收须在独立全新测试安装中执行，不与已同意业务用例共用安装状态。
 */
class AcceptedPrivacyRule : TestRule {
    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            val app = ApplicationProvider.getApplicationContext<LiZhangApplication>()
            assertTrue("业务测试前置：当前隐私告知已明确确认且同步写盘成功",
                app.appContainer.privacyConsentRepository.acceptCurrentPolicy(
                    loadedConsentDocuments(app.appContainer.legalDocumentRepository)))
            assertTrue(app.appContainer.canProcessPersonalData)
            base.evaluate()
        }
    }
}
