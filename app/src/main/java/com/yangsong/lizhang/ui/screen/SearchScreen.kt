package com.yangsong.lizhang.ui.screen
import com.yangsong.lizhang.ui.component.GlassCard
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onRecordClick: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.nav_search), onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                label = stringResource(R.string.search_hint),
                leadingIcon = Icons.Outlined.Search,
            )
            GlassCard(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(GlassTokens.Radius),
            ) {
                when {
                    state.error -> ErrorState(viewModel::retry)
                    state.query.isBlank() -> EmptyState(
                        stringResource(R.string.search_intro),
                        image = R.drawable.page_contacts_cat,
                    )
                    state.records.isEmpty() -> EmptyState(
                        stringResource(R.string.search_no_result),
                        image = R.drawable.page_contacts_cat,
                    )
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        item { SectionHeader(stringResource(R.string.search_result)) }
                        itemsIndexed(
                            items = state.records,
                            key = { _, item -> item.record.id },
                        ) { index, item ->
                            GiftRecordListItem(item) {
                                onRecordClick(item.record.id)
                            }
                            if (index < state.records.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }
    }
}
