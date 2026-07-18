package com.yangsong.lizhang.ui.screen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.theme.LiZhangSpacing
import com.yangsong.lizhang.ui.viewmodel.*
@Composable fun HomeScreen(viewModel:HomeViewModel,onNavigate:(AppDestination)->Unit){val state by viewModel.uiState.collectAsStateWithLifecycle();HomeContent(state,onNavigate)}
@Composable fun HomeContent(state:HomeUiState,onNavigate:(AppDestination)->Unit){
    Scaffold(topBar={AppTopBar(stringResource(R.string.app_name)){IconButton({onNavigate(AppDestination.Search)}){Icon(Icons.Outlined.Search,stringResource(R.string.action_search))}}},bottomBar={BottomNavBar(AppDestination.Home,onNavigate)},floatingActionButton={ExtendedFloatingActionButton(onClick={onNavigate(AppDestination.AddGift)},icon={Icon(Icons.Outlined.Add,null)},text={Text(stringResource(R.string.action_add_gift))})}){padding->
        when{state.isLoading->LoadingState();state.error->ErrorState{};else->LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=LiZhangSpacing.md),contentPadding=PaddingValues(bottom=LiZhangSpacing.xl),verticalArrangement=Arrangement.spacedBy(LiZhangSpacing.md)){
            item{Text(stringResource(R.string.app_tagline),color=MaterialTheme.colorScheme.onSurfaceVariant)}
            item{Row(horizontalArrangement=Arrangement.spacedBy(LiZhangSpacing.sm)){AmountSummaryCard(stringResource(R.string.home_year_received),state.received,Modifier.weight(1f),MaterialTheme.colorScheme.secondary);AmountSummaryCard(stringResource(R.string.home_year_given),state.given,Modifier.weight(1f),MaterialTheme.colorScheme.tertiary)}}
            item{AmountSummaryCard(stringResource(R.string.home_net),state.net,Modifier.fillMaxWidth())}
            item{Card(onClick={onNavigate(AppDestination.OcrImport)},colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Row(Modifier.fillMaxWidth().padding(LiZhangSpacing.md)){Icon(Icons.Outlined.DocumentScanner,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(LiZhangSpacing.md));Column(Modifier.weight(1f)){Text(stringResource(R.string.home_ocr_title),style=MaterialTheme.typography.titleMedium);Text(stringResource(R.string.home_ocr_desc),color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.Outlined.ChevronRight,null)}}}
            item{SectionHeader(stringResource(R.string.home_recent))}
            if(state.recentRecords.isEmpty())item{EmptyState(stringResource(R.string.home_empty_title),stringResource(R.string.home_empty_desc),stringResource(R.string.action_add_gift)){onNavigate(AppDestination.AddGift)}}else items(state.recentRecords,key={it.record.id}){GiftRecordListItem(it);HorizontalDivider()}
        }}
    }
}
