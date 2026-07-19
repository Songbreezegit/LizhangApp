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
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.mapper.labelRes
import com.yangsong.lizhang.ui.theme.*
import com.yangsong.lizhang.ui.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(viewModel:ContactDetailViewModel,onBack:()->Unit,onEdit:()->Unit,onAdd:()->Unit){
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var filter by remember{mutableStateOf<GiftDirection?>(null)}
    var showFilter by remember{mutableStateOf(false)}
    var recordToDelete by remember{mutableStateOf<com.yangsong.lizhang.domain.model.GiftRecord?>(null)}
    val visibleRecords=remember(state.records,filter){state.records.filter{filter==null||it.direction==filter}}
    Scaffold(
        topBar={AppTopBar(state.contact?.name?:stringResource(R.string.nav_contact_detail),onBack){IconButton(onEdit){Icon(Icons.Outlined.Edit,stringResource(R.string.contact_edit))}}},
    ){padding->
        when{
            state.isLoading->LoadingState()
            state.error->ErrorState{}
            else->LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                item{PageIllustration(R.drawable.page_contacts_cat,Modifier.fillMaxWidth().height(135.dp))}
                item{state.contact?.let{contact->Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(2.dp)){Column(Modifier.fillMaxWidth().padding(18.dp)){Text(contact.name,style=MaterialTheme.typography.titleLarge);Text(listOfNotNull(contact.relationship,contact.phone).joinToString(" · "),color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}
                item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){AmountSummaryCard(stringResource(R.string.contact_received),state.received,Modifier.weight(1f),CoralStrong);AmountSummaryCard(stringResource(R.string.contact_given),state.given,Modifier.weight(1f),MintPrimary)}}
                item{PrimaryButton(stringResource(R.string.contact_add_history),onAdd,Modifier.fillMaxWidth())}
                item{Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(2.dp)){Column(Modifier.padding(horizontal=16.dp)){SectionHeader(stringResource(R.string.contact_history),stringResource(R.string.contact_filter)){showFilter=true};if(visibleRecords.isEmpty())EmptyState(stringResource(R.string.home_empty_title),image=R.drawable.page_add_cat)else visibleRecords.forEachIndexed{index,record->GiftRecordListItem(GiftRecordWithContact(record,state.contact?.name.orEmpty())){recordToDelete=record};if(index<visibleRecords.lastIndex)HorizontalDivider(color=MaterialTheme.colorScheme.outline)}}}}
            }
        }
    }
    if(showFilter)ModalBottomSheet(onDismissRequest={showFilter=false}){Column(Modifier.fillMaxWidth().padding(horizontal=18.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(stringResource(R.string.contact_filter),style=MaterialTheme.typography.titleLarge);FilterOption(stringResource(R.string.action_all),filter==null){filter=null;showFilter=false};GiftDirection.entries.forEach{direction->FilterOption(stringResource(direction.labelRes()),filter==direction){filter=direction;showFilter=false}}}}
    recordToDelete?.let{record->ConfirmDialog(title=stringResource(R.string.record_delete_title),message=stringResource(R.string.record_delete_message),confirmText=stringResource(R.string.action_delete),cancelText=stringResource(R.string.action_cancel),onConfirm={recordToDelete=null;viewModel.deleteRecord(record)},onDismiss={recordToDelete=null},danger=true)}
}

@Composable private fun FilterOption(label:String,selected:Boolean,onClick:()->Unit){Surface(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=if(selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface){Row(Modifier.padding(16.dp)){Text(label,Modifier.weight(1f));if(selected)Text("✓",color=MaterialTheme.colorScheme.primary)}}}
