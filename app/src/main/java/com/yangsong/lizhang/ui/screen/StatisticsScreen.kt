package com.yangsong.lizhang.ui.screen
import com.yangsong.lizhang.ui.onboarding.FeatureGuideTarget
import com.yangsong.lizhang.ui.onboarding.featureGuideTarget
import com.yangsong.lizhang.ui.component.AppScaffold
import com.yangsong.lizhang.ui.component.GlassCard
import com.yangsong.lizhang.ui.component.GlassChip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.mapper.labelRes
import com.yangsong.lizhang.ui.theme.*
import com.yangsong.lizhang.ui.viewmodel.*

@Composable
fun StatisticsScreen(viewModel:StatisticsViewModel,onBack:()->Unit){
    val state= viewModel.uiState.collectAsStateWithLifecycle().value
    AppScaffold(topBar={AppTopBar(stringResource(R.string.nav_statistics),onBack)}){padding->
        when{
            state.isLoading->LoadingState()
            state.error->ErrorState(viewModel::retry)
            else->LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp),
                verticalArrangement=Arrangement.spacedBy(14.dp),
            ){
                item(key = "年度选择", contentType = "年度选择"){LazyRow(modifier=Modifier.featureGuideTarget(FeatureGuideTarget.STATISTICS),horizontalArrangement=Arrangement.spacedBy(8.dp)){items(state.years, key = { it }, contentType = { "年份" }){year->GlassChip(year==state.year,{viewModel.selectYear(year)},{Text(stringResource(R.string.year_format,year))})}}}
                item(key = "收送汇总", contentType = "收送汇总"){Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){AmountSummaryCard(stringResource(R.string.home_year_received),state.received,Modifier.weight(1f),MaterialTheme.colorScheme.primary);AmountSummaryCard(stringResource(R.string.home_year_given),state.given,Modifier.weight(1f),MaterialTheme.colorScheme.secondary)}}
                item(key = "净往来", contentType = "金额汇总"){AmountSummaryCard(stringResource(R.string.home_net),state.net,Modifier.fillMaxWidth(),if(state.net>=0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)}
                if(state.received==0L&&state.given==0L){
                    item(key = "无往来", contentType = "空状态"){EmptyState(stringResource(R.string.stats_empty),image=R.drawable.page_statistics_cat)}
                }else{
                    item(key = "月度统计", contentType = "月度图表"){StatsCard(stringResource(R.string.stats_monthly)){MonthlyChart(state.months)}}
                    item(key = "事件统计", contentType = "事件统计") {
                        StatsCard(stringResource(R.string.stats_event)) {
                            state.events.forEach { stat ->
                                EventStatLine(stat)
                            }
                        }
                    }
                    item(key = "联系人统计", contentType = "联系人统计"){StatsCard(stringResource(R.string.stats_contact)){state.contacts.forEach{(name,amount)->StatLine(name,amount)}}}
                }
            }
        }
    }
}

@Composable private fun StatsCard(title:String,content:@Composable ColumnScope.()->Unit){GlassCard(shape=RoundedCornerShape(GlassTokens.Radius)){Column(Modifier.padding(16.dp)){SectionHeader(title);content()}}}
@Composable
private fun MonthlyChart(months: List<MonthStat>) {
    val heights = remember(months) {
        val max = (months.maxOfOrNull { it.received + it.given } ?: 1).coerceAtLeast(1)
        months.map { (100f * (it.received + it.given) / max).dp.coerceAtLeast(3.dp) }
    }
    val shape = remember { RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp) }
    Row(
        Modifier.fillMaxWidth().height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        months.forEachIndexed { index, month ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().height(heights[index]).background(
                        if (month.month % 2 == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        shape,
                    ),
                )
                Text("${month.month}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
@Composable private fun StatLine(label:String,amount:Long){Row(Modifier.fillMaxWidth().padding(vertical=9.dp)){Text(label,Modifier.weight(1f));Text(com.yangsong.lizhang.ui.mapper.displayAmount(amount),fontWeight=FontWeight.SemiBold)}}

@Composable
private fun EventStatLine(stat: EventStat) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(stat.eventType.labelRes()),
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(GiftDirection.RECEIVED.labelRes()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                com.yangsong.lizhang.ui.mapper.displayAmount(stat.received),
                Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(GiftDirection.GIVEN.labelRes()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                com.yangsong.lizhang.ui.mapper.displayAmount(stat.given),
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
