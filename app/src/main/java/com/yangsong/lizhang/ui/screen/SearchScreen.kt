package com.yangsong.lizhang.ui.screen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.viewmodel.SearchViewModel
@Composable fun SearchScreen(viewModel:SearchViewModel,onBack:()->Unit){val s by viewModel.uiState.collectAsStateWithLifecycle();Scaffold(topBar={AppTopBar(stringResource(R.string.nav_search),onBack)}){p->Column(Modifier.fillMaxSize().padding(p).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){AppTextField(s.query,viewModel::updateQuery,stringResource(R.string.search_hint),leadingIcon=Icons.Outlined.Search);Card(Modifier.fillMaxWidth().weight(1f),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(2.dp)){Column(Modifier.padding(horizontal=16.dp)){when{s.query.isBlank()->EmptyState(stringResource(R.string.search_intro),image=R.drawable.page_contacts_cat);s.records.isEmpty()->EmptyState(stringResource(R.string.search_no_result),image=R.drawable.page_contacts_cat);else->{SectionHeader(stringResource(R.string.search_result));s.records.forEachIndexed{i,item->GiftRecordListItem(item);if(i<s.records.lastIndex)HorizontalDivider(color=MaterialTheme.colorScheme.outline)}}}}}}}}
