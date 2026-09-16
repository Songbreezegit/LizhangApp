package com.yangsong.lizhang.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.core.util.CurrencyFormatter
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.mapper.labelRes
import com.yangsong.lizhang.ui.theme.*
import com.yangsong.lizhang.ui.viewmodel.*

@Composable
fun StatisticsScreen(viewModel:StatisticsViewModel,onBack:()->Unit){
    val state= viewModel.uiState.collectAsStateWithLifecycle().value
    Scaffold(topBar={AppTopBar(stringResource(R.string.nav_statistics),onBack)}){padding->
        when{
            state.isLoading->LoadingState()
            state.error->ErrorState(viewModel::retry)
            else->LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp),
                verticalArrangement=Arrangement.spacedBy(14.dp),
            ){
                item{PageIllustration(R.drawable.page_statistics_cat,Modifier.fillMaxWidth().height(145.dp))}
                item{LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(state.years){year->FilterChip(year==state.year,{viewModel.selectYear(year)},{Text(stringResource(R.string.year_format,year))})}}}
                item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){AmountSummaryCard(stringResource(R.string.home_year_received),state.received,Modifier.weight(1f),CoralStrong);AmountSummaryCard(stringResource(R.string.home_year_given),state.given,Modifier.weight(1f),MintPrimary)}}
                item{AmountSummaryCard(stringResource(R.string.home_net),state.net,Modifier.fillMaxWidth(),if(state.net>=0)CoralStrong else MintPrimary)}
                if(state.received==0L&&state.given==0L){
                    item{EmptyState(stringResource(R.string.stats_empty),image=R.drawable.page_statistics_cat)}
                }else{
                    item{StatsCard(stringResource(R.string.stats_monthly)){MonthlyChart(state.months)}}
                    item {
                        StatsCard(stringResource(R.string.stats_event)) {
                            state.events.forEach { stat ->
                                EventStatLine(stat)
                            }
                        }
                    }
                    item{StatsCard(stringResource(R.string.stats_contact)){state.contacts.forEach{(name,amount)->StatLine(name,amount)}}}
                }
            }
        }
    }
}

@Composable private fun StatsCard(title:String,content:@Composable ColumnScope.()->Unit){Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(2.dp)){Column(Modifier.padding(16.dp)){SectionHeader(title);content()}}}
@Composable private fun MonthlyChart(months:List<MonthStat>){val max=(months.maxOfOrNull{it.received+it.given}?:1).coerceAtLeast(1);Row(Modifier.fillMaxWidth().height(150.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.Bottom){months.forEach{month->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.fillMaxWidth().height((100f*(month.received+month.given)/max).dp.coerceAtLeast(3.dp)).background(if(month.month%2==0)CoralPrimary else LavenderPrimary,RoundedCornerShape(topStart=5.dp,topEnd=5.dp)));Text("${month.month}",style=MaterialTheme.typography.labelSmall)}}}}
@Composable private fun StatLine(label:String,amount:Long){Row(Modifier.fillMaxWidth().padding(vertical=9.dp)){Text(label,Modifier.weight(1f));Text(CurrencyFormatter.formatCents(amount),fontWeight=FontWeight.SemiBold)}}

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
                CurrencyFormatter.formatCents(stat.received),
                Modifier.weight(1f),
                color = CoralStrong,
            )
            Text(
                stringResource(GiftDirection.GIVEN.labelRes()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                CurrencyFormatter.formatCents(stat.given),
                color = MintPrimary,
            )
        }
    }
}
