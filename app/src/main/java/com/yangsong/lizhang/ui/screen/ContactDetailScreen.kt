package com.yangsong.lizhang.ui.screen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.theme.*
import com.yangsong.lizhang.ui.viewmodel.*
@Composable fun ContactDetailScreen(viewModel:ContactDetailViewModel,onBack:()->Unit,onAdd:()->Unit){val s by viewModel.uiState.collectAsStateWithLifecycle();Scaffold(topBar={AppTopBar(s.contact?.name?:stringResource(R.string.nav_contact_detail),onBack){IconButton({}){Icon(Icons.Outlined.Edit,stringResource(R.string.contact_edit))}}}){p->when{s.isLoading->LoadingState();s.error->ErrorState{};else->LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){item{PageIllustration(R.drawable.page_contacts_cat,Modifier.fillMaxWidth().height(135.dp))};item{s.contact?.let{Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(2.dp)){Column(Modifier.fillMaxWidth().padding(18.dp)){Text(it.name,style=MaterialTheme.typography.titleLarge);Text(listOfNotNull(it.relationship,it.phone).joinToString(" · "),color=MaterialTheme.colorScheme.onSurfaceVariant)}}}};item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){AmountSummaryCard(stringResource(R.string.contact_received),s.received,Modifier.weight(1f),CoralStrong);AmountSummaryCard(stringResource(R.string.contact_given),s.given,Modifier.weight(1f),MintPrimary)}};item{PrimaryButton(stringResource(R.string.contact_add_history),onAdd,Modifier.fillMaxWidth())};item{Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(2.dp)){Column(Modifier.padding(horizontal=16.dp)){SectionHeader(stringResource(R.string.contact_history),stringResource(R.string.contact_filter)){};if(s.records.isEmpty())EmptyState(stringResource(R.string.home_empty_title),image=R.drawable.page_add_cat)else s.records.forEachIndexed{i,r->GiftRecordListItem(GiftRecordWithContact(r,s.contact?.name.orEmpty()));if(i<s.records.lastIndex)HorizontalDivider(color=MaterialTheme.colorScheme.outline)}}}}}}}}
