package com.yangsong.lizhang.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.core.util.DateFormatter
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.viewmodel.*
import kotlinx.coroutines.delay
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGiftScreen(viewModel:GiftEditorViewModel,onBack:()->Unit,onNavigate:(AppDestination)->Unit,showBottomNavigation:Boolean=true){
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar=remember{SnackbarHostState()}
    var showContacts by remember{mutableStateOf(false)}
    var showCreateContact by remember{mutableStateOf(false)}
    var showDatePicker by remember{mutableStateOf(false)}
    val savedMessage=stringResource(if(state.isEditing)R.string.record_updated else R.string.saved_success)
    val operationFailed=stringResource(R.string.record_save_failed)
    LaunchedEffect(state.isSaved){if(state.isSaved){snackbar.showSnackbar(savedMessage);delay(450);onBack()}}
    LaunchedEffect(state.operationFailed){if(state.operationFailed)snackbar.showSnackbar(operationFailed)}
    Scaffold(
        topBar={AppTopBar(stringResource(if(state.isEditing)R.string.record_edit else R.string.nav_add_gift),onBack)},
        bottomBar={if(showBottomNavigation)BottomNavBar(AppDestination.AddGift,onNavigate)},
        snackbarHost={SnackbarHost(snackbar)},
    ){padding->
        when{state.isLoading->Box(Modifier.fillMaxSize().padding(padding)){LoadingState()};state.loadFailed->Box(Modifier.fillMaxSize().padding(padding)){ErrorState{}};else->Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            PageIllustration(R.drawable.page_add_cat,Modifier.fillMaxWidth().height(190.dp))
            Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(2.dp)){
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                    Text(stringResource(R.string.field_contact),style=MaterialTheme.typography.titleMedium)
                    SelectionRow(
                        text=state.contacts.firstOrNull{it.id==state.contactId}?.name?:stringResource(R.string.field_contact_hint),
                        icon=Icons.Outlined.PersonSearch,
                        onClick={showContacts=true},
                    )
                    if(state.validationError==GiftRecordValidationError.CONTACT_REQUIRED)Text(stringResource(R.string.error_contact_required),color=MaterialTheme.colorScheme.error)
                    AmountTextField(state.amount,{value->viewModel.update{it.copy(amount=value)}},if(state.validationError==GiftRecordValidationError.AMOUNT_INVALID)stringResource(R.string.error_amount_invalid)else null)
                    SelectionRow(DateFormatter.format(state.eventDate),Icons.Outlined.CalendarMonth){showDatePicker=true}
                    DirectionSelector(state.direction){value->viewModel.update{it.copy(direction=value)}}
                    EventTypeSelector(state.eventType){value->viewModel.update{it.copy(eventType=value)}}
                    AppTextField(state.notes,{value->viewModel.update{it.copy(notes=value)}},stringResource(R.string.field_notes),placeholder=stringResource(R.string.field_notes_hint))
                    PrimaryButton(stringResource(R.string.action_save_record),viewModel::save,Modifier.fillMaxWidth(),state.isSaving)
                }
            }
            Spacer(Modifier.height(16.dp))
        }}
    }
    if(showContacts)ContactPickerSheet(state.contacts,state.contactId,{contact->viewModel.update{it.copy(contactId=contact.id)};showContacts=false},{showCreateContact=true},onDismiss={showContacts=false})
    if(showCreateContact)QuickContactDialog(state.isCreatingContact,{name,phone,relationship->viewModel.createContact(name,phone,relationship);showCreateContact=false;showContacts=false},{showCreateContact=false})
    if(showDatePicker)GiftDatePicker(state.eventDate,{millis->viewModel.update{it.copy(eventDate=millis)};showDatePicker=false},{showDatePicker=false})
}

@Composable private fun SelectionRow(text:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit){Surface(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.background){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(12.dp));Text(text,Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ContactPickerSheet(contacts:List<Contact>,selectedId:Long?,onSelect:(Contact)->Unit,onCreate:()->Unit,onDismiss:()->Unit){var query by remember{mutableStateOf("")};val shown=remember(query,contacts){contacts.filter{it.name.contains(query,true)||it.phone.orEmpty().contains(query)}};ModalBottomSheet(onDismissRequest=onDismiss){Column(Modifier.fillMaxWidth().padding(horizontal=18.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(stringResource(R.string.contact_choose),style=MaterialTheme.typography.titleLarge);AppTextField(query,{query=it},stringResource(R.string.contact_search_hint),leadingIcon=Icons.Outlined.Search);shown.forEach{contact->Surface(onClick={onSelect(contact)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=if(contact.id==selectedId)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.AccountCircle,null);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(contact.name);Text(contact.phone?:contact.relationship.orEmpty(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};if(contact.id==selectedId)Icon(Icons.Outlined.Check,null,tint=MaterialTheme.colorScheme.primary)}}};SecondaryButton(stringResource(R.string.contact_create_quick),onCreate,Modifier.fillMaxWidth(),Icons.Outlined.PersonAdd)}}}

@Composable private fun QuickContactDialog(isSaving:Boolean,onConfirm:(String,String,String)->Unit,onDismiss:()->Unit){var name by remember{mutableStateOf("")};var phone by remember{mutableStateOf("")};var relationship by remember{mutableStateOf("")};var attempted by remember{mutableStateOf(false)};AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.contact_create_quick))},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){AppTextField(name,{name=it},stringResource(R.string.contact_name),error=if(attempted&&name.isBlank())stringResource(R.string.contact_name_required)else null);AppTextField(phone,{phone=it},stringResource(R.string.contact_phone));AppTextField(relationship,{relationship=it},stringResource(R.string.contact_relationship))}},confirmButton={Button({attempted=true;if(name.isNotBlank())onConfirm(name,phone,relationship)},enabled=!isSaving){Text(stringResource(R.string.action_confirm))}},dismissButton={TextButton(onDismiss){Text(stringResource(R.string.action_cancel))}})}

@Composable
private fun GiftDatePicker(initial:Long,onConfirm:(Long)->Unit,onDismiss:()->Unit){
    val initialDate=remember(initial){Calendar.getInstance().apply{timeInMillis=initial}}
    var year by remember{mutableIntStateOf(initialDate.get(Calendar.YEAR))}
    var month by remember{mutableIntStateOf(initialDate.get(Calendar.MONTH)+1)}
    var day by remember{mutableIntStateOf(initialDate.get(Calendar.DAY_OF_MONTH))}
    val maxDay=remember(year,month){Calendar.getInstance().apply{set(Calendar.YEAR,year);set(Calendar.MONTH,month-1)}.getActualMaximum(Calendar.DAY_OF_MONTH)}
    LaunchedEffect(maxDay){if(day>maxDay)day=maxDay}
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text(stringResource(R.string.date_choose))},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){IconButton({year--}){Icon(Icons.Outlined.ChevronLeft,stringResource(R.string.action_back))};Text(stringResource(R.string.year_format,year),style=MaterialTheme.typography.titleMedium);IconButton({year++}){Icon(Icons.Outlined.ChevronRight,null)}}
            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items((1..12).toList()){value->FilterChip(value==month,{month=value},{Text(stringResource(R.string.month_format,value))})}}
            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items((1..maxDay).toList()){value->FilterChip(value==day,{day=value},{Text(stringResource(R.string.day_format,value))})}}
        }},
        confirmButton={TextButton({val date=Calendar.getInstance().apply{set(year,month-1,day,0,0,0);set(Calendar.MILLISECOND,0)};onConfirm(date.timeInMillis)}){Text(stringResource(R.string.action_done))}},
        dismissButton={TextButton(onDismiss){Text(stringResource(R.string.action_cancel))}},
    )
}
