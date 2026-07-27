package com.yangsong.lizhang.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.yangsong.lizhang.domain.model.*
import com.yangsong.lizhang.ui.screen.HomeContent
import com.yangsong.lizhang.ui.theme.LiZhangSpacing
import com.yangsong.lizhang.ui.theme.LiZhangTheme
import com.yangsong.lizhang.ui.viewmodel.HomeUiState

private val previewRecord = GiftRecordWithContact(GiftRecord(1,1,80000,EventType.WEDDING,System.currentTimeMillis(),GiftDirection.RECEIVED,"老同学婚礼"),"张建国")

@Preview(name="组件目录·浅色",showBackground=true,widthDp=390)
@Preview(name="组件目录·深色",showBackground=true,widthDp=390,uiMode=0x20)
@Composable private fun ComponentsPreview(){LiZhangTheme{Surface{Column(Modifier.padding(LiZhangSpacing.md),verticalArrangement=Arrangement.spacedBy(LiZhangSpacing.md)){AmountSummaryCard("本年收到",168800);GiftRecordListItem(previewRecord);DirectionSelector(GiftDirection.RECEIVED){};PrimaryButton("保存记录",{})}}}}

@Preview(name="首页·浅色",showSystemUi=true,widthDp=390,heightDp=844)
@Preview(name="首页·深色",showSystemUi=true,widthDp=390,heightDp=844,uiMode=0x20)
@Composable private fun HomePreview(){LiZhangTheme{HomeContent(HomeUiState(isLoading=false,recentRecords=listOf(previewRecord),received=168800,given=88000),{})}}
