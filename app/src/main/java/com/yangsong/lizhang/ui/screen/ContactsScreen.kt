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
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.theme.LiZhangSpacing
import com.yangsong.lizhang.ui.viewmodel.*
@Composable fun ContactsScreen(viewModel:ContactsViewModel,onContactClick:(Long)->Unit,onNavigate:(AppDestination)->Unit){val s by viewModel.uiState.collectAsStateWithLifecycle();Scaffold(topBar={AppTopBar(stringResource(R.string.nav_contacts))},bottomBar={BottomNavBar(AppDestination.Contacts,onNavigate)}){p->Column(Modifier.fillMaxSize().padding(p).padding(horizontal=LiZhangSpacing.md)){AppTextField(s.query,viewModel::updateQuery,stringResource(R.string.contact_search_hint),leadingIcon=Icons.Outlined.Search);Row(horizontalArrangement=Arrangement.spacedBy(LiZhangSpacing.sm)){FilterChip(s.sort==ContactSort.RECENT,{viewModel.updateSort(ContactSort.RECENT)},{Text(stringResource(R.string.contact_sort_recent))});FilterChip(s.sort==ContactSort.NAME,{viewModel.updateSort(ContactSort.NAME)},{Text(stringResource(R.string.contact_sort_name))})};when{ s.isLoading->LoadingState();s.error->ErrorState{};s.contacts.isEmpty()->EmptyState(if(s.query.isBlank())stringResource(R.string.contact_empty)else stringResource(R.string.contact_no_result));else->LazyColumn{items(s.contacts,key={it.contact.id}){ContactListItem(it){onContactClick(it.contact.id)};HorizontalDivider()}}}}}}
