package com.yangsong.lizhang.ui.screen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.core.util.DateFormatter
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.theme.LiZhangSpacing
import com.yangsong.lizhang.ui.viewmodel.*
@Composable fun AddGiftScreen(viewModel:GiftEditorViewModel,onBack:()->Unit){
    val s by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(s.isSaved){if(s.isSaved)onBack()}
    Scaffold(topBar={AppTopBar(stringResource(R.string.nav_add_gift),onBack)}){p->
        Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(LiZhangSpacing.md),verticalArrangement=Arrangement.spacedBy(LiZhangSpacing.md)){
            Text(stringResource(R.string.field_contact),style=MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(LiZhangSpacing.sm)){items(s.contacts,key={it.id}){c->FilterChip(s.contactId==c.id,{viewModel.update{it.copy(contactId=c.id)}},{Text(c.name)})}}
            if(s.validationError==GiftRecordValidationError.CONTACT_REQUIRED)Text(stringResource(R.string.error_contact_required),color=MaterialTheme.colorScheme.error)
            AmountTextField(s.amount,{v->viewModel.update{it.copy(amount=v)}},if(s.validationError==GiftRecordValidationError.AMOUNT_INVALID)stringResource(R.string.error_amount_invalid)else null)
            AppTextField(DateFormatter.format(s.eventDate),{},stringResource(R.string.field_date),leadingIcon=Icons.Outlined.CalendarMonth,readOnly=true)
            DirectionSelector(s.direction){v->viewModel.update{it.copy(direction=v)}}
            EventTypeSelector(s.eventType){v->viewModel.update{it.copy(eventType=v)}}
            AppTextField(s.notes,{v->viewModel.update{it.copy(notes=v)}},stringResource(R.string.field_notes),placeholder=stringResource(R.string.field_notes_hint))
            Spacer(Modifier.height(LiZhangSpacing.md))
            PrimaryButton(stringResource(R.string.action_save_record),viewModel::save,Modifier.fillMaxWidth(),s.isSaving)
        }
    }
}
