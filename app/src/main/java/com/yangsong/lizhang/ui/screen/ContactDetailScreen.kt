package com.yangsong.lizhang.ui.screen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.theme.LiZhangSpacing
import com.yangsong.lizhang.ui.viewmodel.*
@Composable fun ContactDetailScreen(viewModel:ContactDetailViewModel,onBack:()->Unit,onAdd:()->Unit){val s by viewModel.uiState.collectAsStateWithLifecycle();Scaffold(topBar={AppTopBar(s.contact?.name?:stringResource(R.string.nav_contact_detail),onBack){IconButton({}){Icon(Icons.Outlined.Edit,stringResource(R.string.contact_edit))}}}){p->when{s.isLoading->LoadingState();s.error->ErrorState{};else->LazyColumn(Modifier.fillMaxSize().padding(p).padding(horizontal=LiZhangSpacing.md),verticalArrangement=Arrangement.spacedBy(LiZhangSpacing.md)){item{s.contact?.let{Text(listOfNotNull(it.relationship,it.phone).joinToString(" · "),color=MaterialTheme.colorScheme.onSurfaceVariant)}};item{Row(horizontalArrangement=Arrangement.spacedBy(LiZhangSpacing.sm)){AmountSummaryCard(stringResource(R.string.contact_received),s.received,Modifier.weight(1f),MaterialTheme.colorScheme.secondary);AmountSummaryCard(stringResource(R.string.contact_given),s.given,Modifier.weight(1f),MaterialTheme.colorScheme.tertiary)}};item{AmountSummaryCard(stringResource(R.string.contact_net),s.net,Modifier.fillMaxWidth())};item{PrimaryButton(stringResource(R.string.contact_add_history),onAdd,Modifier.fillMaxWidth())};item{SectionHeader(stringResource(R.string.contact_history),stringResource(R.string.contact_filter)){} };if(s.records.isEmpty())item{EmptyState(stringResource(R.string.home_empty_title))}else items(s.records,key={it.id}){GiftRecordListItem(GiftRecordWithContact(it,s.contact?.name.orEmpty()));HorizontalDivider()}}}}}
