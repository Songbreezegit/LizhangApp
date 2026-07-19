package com.yangsong.lizhang.ui.screen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.viewmodel.*
@Composable fun ContactsScreen(viewModel:ContactsViewModel,onContactClick:(Long)->Unit,onAddContact:()->Unit){val s by viewModel.uiState.collectAsStateWithLifecycle();Scaffold(topBar={AppTopBar(stringResource(R.string.nav_contacts))},floatingActionButton={Box(Modifier.padding(bottom=96.dp)){ExtendedFloatingActionButton(onClick=onAddContact,icon={Icon(Icons.Outlined.PersonAdd,null)},text={Text(stringResource(R.string.contact_create))})}}){p->LazyColumn(Modifier.fillMaxSize().padding(p),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=8.dp,bottom=124.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){item{PageIllustration(R.drawable.page_contacts_cat,Modifier.fillMaxWidth().height(190.dp))};item{AppTextField(s.query,viewModel::updateQuery,stringResource(R.string.contact_search_hint),leadingIcon=Icons.Outlined.Search)};item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(s.sort==ContactSort.RECENT,{viewModel.updateSort(ContactSort.RECENT)},{Text(stringResource(R.string.contact_sort_recent))});FilterChip(s.sort==ContactSort.NAME,{viewModel.updateSort(ContactSort.NAME)},{Text(stringResource(R.string.contact_sort_name))})}};item{Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(2.dp)){when{s.isLoading->Box(Modifier.height(220.dp)){LoadingState()};s.error->ErrorState{};s.contacts.isEmpty()->EmptyState(if(s.query.isBlank())stringResource(R.string.contact_empty)else stringResource(R.string.contact_no_result),image=R.drawable.page_contacts_cat);else->Column(Modifier.padding(horizontal=16.dp)){s.contacts.forEachIndexed{i,item->ContactListItem(item){onContactClick(item.contact.id)};if(i<s.contacts.lastIndex)HorizontalDivider(color=MaterialTheme.colorScheme.outline)}}}}}}}}
