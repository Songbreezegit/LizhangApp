package com.yangsong.lizhang.ui.screen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.core.util.CurrencyFormatter
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.mapper.labelRes
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.theme.LiZhangSpacing
import com.yangsong.lizhang.ui.viewmodel.*
@Composable fun StatisticsScreen(viewModel:StatisticsViewModel,onNavigate:(AppDestination)->Unit){val s by viewModel.uiState.collectAsStateWithLifecycle();Scaffold(topBar={AppTopBar(stringResource(R.string.nav_statistics))},bottomBar={BottomNavBar(AppDestination.Statistics,onNavigate)}){p->when{s.isLoading->LoadingState();s.error->ErrorState{};else->LazyColumn(Modifier.fillMaxSize().padding(p).padding(horizontal=LiZhangSpacing.md),verticalArrangement=Arrangement.spacedBy(LiZhangSpacing.md)){item{SectionHeader(stringResource(R.string.stats_year));LazyRow(horizontalArrangement=Arrangement.spacedBy(LiZhangSpacing.sm)){items(s.years){y->FilterChip(y==s.year,{viewModel.selectYear(y)},{Text(y.toString())})}}};item{Row(horizontalArrangement=Arrangement.spacedBy(LiZhangSpacing.sm)){AmountSummaryCard(stringResource(R.string.home_year_received),s.received,Modifier.weight(1f),MaterialTheme.colorScheme.secondary);AmountSummaryCard(stringResource(R.string.home_year_given),s.given,Modifier.weight(1f),MaterialTheme.colorScheme.tertiary)}};item{AmountSummaryCard(stringResource(R.string.home_net),s.net,Modifier.fillMaxWidth())};if(s.received==0L&&s.given==0L)item{EmptyState(stringResource(R.string.stats_empty))}else{item{SectionHeader(stringResource(R.string.stats_monthly));MonthlyChart(s.months)};item{SectionHeader(stringResource(R.string.stats_event));s.events.forEach{(e,a)->StatLine(stringResource(e.labelRes()),a)}};item{SectionHeader(stringResource(R.string.stats_contact));s.contacts.forEach{(n,a)->StatLine(n,a)}}}}}}}
@Composable private fun MonthlyChart(months:List<MonthStat>){val max=(months.maxOfOrNull{it.received+it.given}?:1).coerceAtLeast(1);Row(Modifier.fillMaxWidth().height(150.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.Bottom){months.forEach{m->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.fillMaxWidth().height((100f*(m.received+m.given)/max).dp.coerceAtLeast(2.dp)).background(MaterialTheme.colorScheme.primary));Text("${m.month}",style=MaterialTheme.typography.labelSmall)}}}}
@Composable private fun StatLine(label:String,amount:Long){Row(Modifier.fillMaxWidth().padding(vertical=LiZhangSpacing.sm)){Text(label,Modifier.weight(1f));Text(CurrencyFormatter.formatCents(amount))}}
