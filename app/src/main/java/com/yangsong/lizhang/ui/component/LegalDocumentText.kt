package com.yangsong.lizhang.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.domain.legal.LegalDocumentBlock
import com.yangsong.lizhang.domain.legal.LegalDocumentSection
import com.yangsong.lizhang.ui.theme.LegalDocumentTypography

/** 首次告知与独立全文页共用排版，整节文字均可选择复制。 */
@Composable
fun LegalDocumentSectionContent(section: LegalDocumentSection, modifier: Modifier = Modifier) {
    val bodyStyle = MaterialTheme.typography.bodyLarge.merge(LegalDocumentTypography.Body)
    val titleStyle = MaterialTheme.typography.titleMedium.merge(LegalDocumentTypography.SectionTitle)
    val subheadingStyle = bodyStyle.merge(LegalDocumentTypography.Subheading)
    val textColor = MaterialTheme.colorScheme.onSurface
    SelectionContainer {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            section.heading?.let {
                Text(it, Modifier.fillMaxWidth().semantics { heading() },
                    style = titleStyle, color = textColor, softWrap = true)
            }
            section.blocks.forEach { block ->
                when (block) {
                    is LegalDocumentBlock.Paragraph -> Text(
                        block.text, Modifier.fillMaxWidth(), style = bodyStyle, color = textColor, softWrap = true,
                    )
                    is LegalDocumentBlock.Heading -> Text(
                        block.text, Modifier.fillMaxWidth().padding(top = 8.dp).semantics { heading() },
                        style = subheadingStyle, color = textColor, softWrap = true,
                    )
                    is LegalDocumentBlock.ListItem -> Row(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(block.marker, Modifier.widthIn(min = 18.dp), style = bodyStyle, color = textColor)
                        Text(block.text, Modifier.weight(1f), style = bodyStyle, color = textColor, softWrap = true)
                    }
                }
            }
        }
    }
}
