package com.yangsong.lizhang.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.yangsong.lizhang.ui.privacy.PrivacyNoticeScreen
import com.yangsong.lizhang.ui.theme.LiZhangTheme

/** 首次告知布局使用纯视图预览，不读取账本、联系人或确认偏好。 */
@PreviewTest
@Preview(name = "首次告知浅色360", locale = "zh-rCN", widthDp = 360, heightDp = 800)
@Preview(name = "首次告知浅色412", locale = "zh-rCN", widthDp = 412, heightDp = 915)
@Preview(name = "首次告知两倍字号", locale = "zh-rCN", widthDp = 320, heightDp = 720, fontScale = 2f)
@Preview(name = "首次告知深色", locale = "zh-rCN", widthDp = 360, heightDp = 800, uiMode = 0x20)
@Composable
fun FreshPrivacyNoticePreview() {
    LiZhangTheme {
        PrivacyNoticeScreen(onAgree = {}, onDecline = {}, onPrivacy = {}, onTerms = {})
    }
}
