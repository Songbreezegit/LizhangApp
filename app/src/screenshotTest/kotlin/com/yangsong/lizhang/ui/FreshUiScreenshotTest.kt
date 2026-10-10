package com.yangsong.lizhang.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.yangsong.lizhang.domain.legal.LegalDocument
import com.yangsong.lizhang.domain.legal.LegalDocumentSection
import com.yangsong.lizhang.domain.legal.LegalDocumentType
import com.yangsong.lizhang.ui.privacy.PrivacyNoticeScreen
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.LegalDocumentUiState

/** 首次告知布局使用纯视图预览，不读取账本、联系人或确认偏好。 */
@PreviewTest
@Preview(name = "首次告知浅色360", locale = "zh-rCN", widthDp = 360, heightDp = 800)
@Preview(name = "首次告知浅色412", locale = "zh-rCN", widthDp = 412, heightDp = 915)
@Preview(name = "首次告知两倍字号", locale = "zh-rCN", widthDp = 320, heightDp = 720, fontScale = 2f)
@Preview(name = "首次告知深色", locale = "zh-rCN", widthDp = 360, heightDp = 800, uiMode = 0x20)
@Composable
fun FreshPrivacyNoticePreview() {
    LiZhangTheme {
        PrivacyNoticeScreen(
            privacyState = previewNoticeDocument(LegalDocumentType.PRIVACY, "隐私政策", "完整隐私说明合成预览正文。"),
            termsState = previewNoticeDocument(LegalDocumentType.TERMS, "用户协议", "完整用户协议合成预览正文。"),
            onAgree = {}, onDecline = {},
        )
    }
}

private fun previewNoticeDocument(type: LegalDocumentType, heading: String, body: String) =
    LegalDocumentUiState(isLoading = false, document = LegalDocument(type, listOf(LegalDocumentSection(heading, body))))
