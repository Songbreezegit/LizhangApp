package com.yangsong.lizhang.ui.screen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.theme.LiZhangSpacing
import com.yangsong.lizhang.ui.viewmodel.SearchViewModel
@Composable fun SearchScreen(viewModel:SearchViewModel,onBack:()->Unit){val s by viewModel.uiState.collectAsStateWithLifecycle();Scaffold(topBar={AppTopBar(stringResource(R.string.nav_search),onBack)}){p->Column(Modifier.fillMaxSize().padding(p).padding(horizontal=LiZhangSpacing.md)){AppTextField(s.query,viewModel::updateQuery,stringResource(R.string.search_hint),leadingIcon=Icons.Outlined.Search);when{s.query.isBlank()->EmptyState(stringResource(R.string.search_intro));s.records.isEmpty()->EmptyState(stringResource(R.string.search_no_result));else->{SectionHeader(stringResource(R.string.search_result));LazyColumn{items(s.records,key={it.record.id}){GiftRecordListItem(it);HorizontalDivider()}}}}}}}
