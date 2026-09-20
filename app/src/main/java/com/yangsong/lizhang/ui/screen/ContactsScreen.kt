package com.yangsong.lizhang.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.component.AppTextField
import com.yangsong.lizhang.ui.component.AppTopBar
import com.yangsong.lizhang.ui.component.ContactListItem
import com.yangsong.lizhang.ui.component.EmptyState
import com.yangsong.lizhang.ui.component.ErrorState
import com.yangsong.lizhang.ui.component.LoadingState
import com.yangsong.lizhang.ui.component.PageIllustration
import com.yangsong.lizhang.ui.viewmodel.ContactSort
import com.yangsong.lizhang.ui.viewmodel.ContactsUiState
import com.yangsong.lizhang.ui.viewmodel.ContactsViewModel
import com.yangsong.lizhang.ui.theme.CoralPrimary

@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    onContactClick: (Long) -> Unit,
    onAddContact: () -> Unit,
    onImportContacts: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ContactsContent(
        state = state,
        onQueryChange = viewModel::updateQuery,
        onSortChange = viewModel::updateSort,
        onContactClick = onContactClick,
        onAddContact = onAddContact,
        onRetry = viewModel::retry,
        onImportContacts = onImportContacts,
    )
}

@Composable
fun ContactsContent(
    state: ContactsUiState,
    onQueryChange: (String) -> Unit,
    onSortChange: (ContactSort) -> Unit,
    onContactClick: (Long) -> Unit,
    onAddContact: () -> Unit,
    onRetry: () -> Unit = {},
    onImportContacts: () -> Unit = {},
) {
    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.nav_contacts)) },
        floatingActionButton = {
            Box(Modifier.padding(bottom = 96.dp)) {
                FloatingActionButton(
                    onClick = onAddContact,
                    containerColor = CoralPrimary,
                    contentColor = Color.White,
                    shape = CircleShape,
                ) {
                    Icon(
                        Icons.Outlined.PersonAdd,
                        stringResource(R.string.contact_create),
                        Modifier.size(28.dp),
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 124.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { PageIllustration(R.drawable.page_contacts_cat, Modifier.fillMaxWidth().height(190.dp)) }
            item {
                Card(onClick = onImportContacts, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.contact_import_entry), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.contact_import_entry_hint), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                AppTextField(
                    state.query,
                    onQueryChange,
                    stringResource(R.string.contact_search_hint),
                    leadingIcon = Icons.Outlined.Search,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        state.sort == ContactSort.RECENT,
                        { onSortChange(ContactSort.RECENT) },
                        { Text(stringResource(R.string.contact_sort_recent)) },
                    )
                    FilterChip(
                        state.sort == ContactSort.NAME,
                        { onSortChange(ContactSort.NAME) },
                        { Text(stringResource(R.string.contact_sort_name)) },
                    )
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    when {
                        state.isLoading -> Box(Modifier.height(220.dp)) { LoadingState() }
                        state.error -> ErrorState(onRetry)
                        state.contacts.isEmpty() -> EmptyState(
                            if (state.query.isBlank()) stringResource(R.string.contact_empty) else stringResource(R.string.contact_no_result),
                            image = R.drawable.page_contacts_cat,
                        )
                        else -> Column(Modifier.padding(horizontal = 16.dp)) {
                            state.contacts.forEachIndexed { index, item ->
                                ContactListItem(item) { onContactClick(item.contact.id) }
                                if (index < state.contacts.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
