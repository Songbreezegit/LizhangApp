package com.yangsong.lizhang.ui.screen
import com.yangsong.lizhang.ui.component.AppScaffold
import com.yangsong.lizhang.ui.component.GlassTextButton
import com.yangsong.lizhang.ui.component.GlassButton
import com.yangsong.lizhang.ui.component.GlassChip
import com.yangsong.lizhang.ui.component.GlassIconButton
import com.yangsong.lizhang.ui.component.GlassCard
import com.yangsong.lizhang.ui.component.GlassDialog

import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yangsong.lizhang.R
import com.yangsong.lizhang.core.util.DateFormatter
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.ui.component.*
import com.yangsong.lizhang.ui.viewmodel.*
import com.yangsong.lizhang.ui.onboarding.FeatureGuideTarget
import com.yangsong.lizhang.ui.onboarding.featureGuideTarget
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import java.util.Calendar
import com.yangsong.lizhang.ui.theme.LocalEffectiveDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGiftScreen(
    viewModel: GiftEditorViewModel,
    onBack: () -> Unit,
    onCreateContact: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar=remember{SnackbarHostState()}
    var showContacts by remember{mutableStateOf(false)}
    var showDatePicker by remember{mutableStateOf(false)}
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    val savedMessage=stringResource(if(state.isEditing)R.string.record_updated else R.string.saved_success)
    val operationFailed=stringResource(R.string.record_save_failed)
    val requestCreateContact = {
        val current = viewModel.uiState.value
        if (!current.isSaving && !current.isSaved) onCreateContact()
    }
    val requestContactSelection = {
        val current = viewModel.uiState.value
        if (!current.isSaving && !current.isSaved) showContacts = true
    }
    val requestBack = {
        when {
            state.isSaving || state.isSaved -> Unit
            state.hasUnsavedChanges -> showDiscardConfirmation = true
            else -> onBack()
        }
    }
    BackHandler(enabled = state.hasUnsavedChanges || state.isSaving || state.isSaved) {
        requestBack()
    }
    GiftSaveFeedbackEffects(state.isSaved, state.operationFailed, snackbar, savedMessage, operationFailed,
        onFailureConsumed = viewModel::consumeOperationFailure, onBack = onBack)
    AddGiftContent(
        state = state,
        onBack = requestBack,
        onContactClick = requestContactSelection,
        onCreateContact = requestCreateContact,
        onAmountChange = { value -> viewModel.update { it.copy(amount = value) } },
        onDateClick = { showDatePicker = true },
        onDirectionChange = { value -> viewModel.update { it.copy(direction = value) } },
        onEventTypeChange = viewModel::selectEvent,
        onNotesChange = { value -> viewModel.update { it.copy(notes = value) } },
        onSave = viewModel::save,
        onRetry = viewModel::retryLoad,
        onCustomEventChange = viewModel::setCustomEvent,
        snackbarHost = { GlassSnackbarHost(snackbar, Modifier.padding(horizontal = 24.dp)) },
    )
    if (showContacts) ContactPickerSheet(
        state.contacts,
        state.contactId,
        onSelect = { contact ->
            viewModel.update { it.copy(contactId = contact.id) }
            showContacts = false
        },
        enabled = !state.isSaving && !state.isSaved,
        onCreate = {
            showContacts = false
            requestCreateContact()
        },
        onDismiss = { showContacts = false },
    )
    if(showDatePicker)GiftDatePicker(state.eventDate,{millis->viewModel.update{it.copy(eventDate=millis)};showDatePicker=false},{showDatePicker=false})
    if (showDiscardConfirmation) {
        DiscardGiftChangesDialog(
            onConfirm = onBack,
            onDismiss = { showDiscardConfirmation = false },
        )
    }
}

@Composable
fun AddGiftContent(
    state: GiftEditorUiState,
    onBack: () -> Unit,
    onContactClick: () -> Unit,
    onAmountChange: (String) -> Unit,
    onDateClick: () -> Unit,
    onDirectionChange: (com.yangsong.lizhang.domain.model.GiftDirection) -> Unit,
    onEventTypeChange: (com.yangsong.lizhang.domain.model.EventType) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    onCustomEventChange: (String) -> Unit = {},
    onCreateContact: () -> Unit = {},
) {
    val hazeState = rememberHazeState()
    var showCustomEvent by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    AppScaffold(
        modifier = Modifier.hazeSource(hazeState),
        topBar={AppTopBar(stringResource(if(state.isEditing)R.string.record_edit else R.string.nav_add_gift),onBack)},
    ){padding->
        when{state.isLoading->Box(Modifier.fillMaxSize().padding(padding)){LoadingState()};state.loadFailed->Box(Modifier.fillMaxSize().padding(padding)){ErrorState(onRetry)};else->Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
                    Column(
                        Modifier.fillMaxWidth().featureGuideTarget(FeatureGuideTarget.RECORD_CONTACT),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.field_contact), style = MaterialTheme.typography.titleMedium)
                        SelectionRow(
                            text = state.contacts.firstOrNull { it.id == state.contactId }?.name
                                ?: stringResource(R.string.field_contact_hint),
                            icon = LiZhangIcons.UserSearch,
                            onClick = onContactClick,
                            enabled = !state.isSaving && !state.isSaved,
                        )
                        CreateContactButton(
                            onClick = onCreateContact,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isSaving && !state.isSaved,
                        )
                    }
                    if(state.validationError==GiftRecordValidationError.CONTACT_REQUIRED)Text(stringResource(R.string.error_contact_required),color=MaterialTheme.colorScheme.error)
                    Box(Modifier.fillMaxWidth().featureGuideTarget(FeatureGuideTarget.RECORD_AMOUNT)) {
                        AmountTextField(state.amount,onAmountChange,if(state.validationError==GiftRecordValidationError.AMOUNT_INVALID)stringResource(R.string.error_amount_invalid)else null)
                    }
                    SelectionRow(com.yangsong.lizhang.ui.mapper.displayDate(state.eventDate),LiZhangIcons.CalendarDays,onDateClick)
                    if (state.validationError == GiftRecordValidationError.DATE_IN_FUTURE) {
                        Text(
                            stringResource(R.string.error_date_in_future),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Box(Modifier.fillMaxWidth().featureGuideTarget(FeatureGuideTarget.RECORD_DIRECTION)) {
                        DirectionSelector(state.direction,onDirectionChange)
                    }
                    EventTypeSelector(state.eventType, onEventTypeChange, state.customEventName) { showCustomEvent = true }
                    AppMultilineTextField(
                        state.notes,
                        onNotesChange,
                        stringResource(R.string.field_notes),
                        placeholder = stringResource(R.string.field_notes_hint),
                    )
                }
            }
            Spacer(Modifier.height(124.dp).testTag("记账底部留白"))
        }}
    }
    if (!state.isLoading && !state.loadFailed) {
        // 内容与反馈共用覆盖层，不改变 Scaffold 可用高度。
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(bottom = 6.dp)) { snackbarHost() }
            GiftSaveBar(state.isSaving, onSave, enabled = !state.isSaved, hazeState = hazeState)
        }
    }
    }
    if (showCustomEvent) CustomEventDialog(state.customEventName.orEmpty(),
        onConfirm = { onCustomEventChange(it); showCustomEvent = false },
        onDismiss = { showCustomEvent = false })
}

@Composable
fun GiftSaveBar(
    isSaving: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hazeState: HazeState,
) {
    val glassShape = RoundedCornerShape(GlassTokens.FloatingRadius)
    val saveFrame = if (LocalEffectiveDarkTheme.current || (!enabled && !isSaving))
        Modifier.frostedGlassFrame(hazeState, glassShape)
        else Modifier.clip(glassShape).background(MaterialTheme.colorScheme.primaryContainer)
    val saveTextColor = if (LocalEffectiveDarkTheme.current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().height(76.dp)
                    .testTag("礼金保存栏")
                    .featureGuideTarget(FeatureGuideTarget.RECORD_SAVE)
                    .pressClickable(enabled = enabled && !isSaving, role = Role.Button, shape = glassShape, onClick = onSave)
                    .then(saveFrame),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(Modifier.size(24.dp).testTag("礼金保存中"),
                        strokeWidth = 2.dp, color = saveTextColor)
                } else {
                    Icon(LiZhangIcons.Check, null, tint = featureIconColor())
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.action_save_record), fontWeight = FontWeight.SemiBold,
                        color = saveTextColor, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
@Composable
fun DiscardGiftChangesDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.record_discard_title)) },
        text = { Text(stringResource(R.string.record_discard_message)) },
        confirmButton = {
            GlassButton(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.record_discard_confirm))
            }
        },
        dismissButton = {
            GlassTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_continue_editing))
            }
        },
    )
}

@Composable private fun SelectionRow(text:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit,enabled:Boolean=true){GlassClickableSurface(onClick=onClick,modifier=Modifier.fillMaxWidth(),enabled=enabled,shape=RoundedCornerShape(18.dp),color=glassColor()){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=featureIconColor());Spacer(Modifier.width(12.dp));Text(text,Modifier.weight(1f));Icon(LiZhangIcons.ChevronRight,null,tint=featureIconColor())}}}

@Composable
private fun CreateContactButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Icon(LiZhangIcons.UserRoundPlus, null, tint = featureIconColor())
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.contact_create))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactPickerSheet(
    contacts: List<Contact>,
    selectedId: Long?,
    onSelect: (Contact) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, contacts) {
        contacts.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.phone.orEmpty().contains(query)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = glassColor().copy(alpha = GlassTokens.DialogAlpha)) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.contact_choose),
                style = MaterialTheme.typography.titleLarge,
            )
            AppTextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.contact_search_hint),
                leadingIcon = LiZhangIcons.Search,
            )
            if (shown.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    EmptyState(stringResource(R.string.contact_no_result))
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = Contact::id) { contact ->
                        GlassClickableSurface(
                            onClick = { onSelect(contact) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = enabled,
                            shape = RoundedCornerShape(16.dp),
                            color = if (contact.id == selectedId) {
                                MaterialTheme.colorScheme.primary.copy(alpha = .12f)
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                        ) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(LiZhangIcons.CircleUserRound, null, tint = featureIconColor())
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(contact.name)
                                    Text(
                                        contact.phone ?: contact.relationship.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (contact.id == selectedId) {
                                    Icon(
                                        LiZhangIcons.Check,
                                        null,
                                        tint = featureIconColor(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            CreateContactButton(
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun GiftDatePicker(initial:Long,onConfirm:(Long)->Unit,onDismiss:()->Unit){
    val today = remember { Calendar.getInstance() }
    val currentYear = today.get(Calendar.YEAR)
    val currentMonth = today.get(Calendar.MONTH) + 1
    val currentDay = today.get(Calendar.DAY_OF_MONTH)
    val initialDate = remember(initial) { Calendar.getInstance().apply { timeInMillis = initial } }
    var year by remember { mutableIntStateOf(initialDate.get(Calendar.YEAR).coerceIn(MIN_GIFT_YEAR, currentYear)) }
    var month by remember { mutableIntStateOf(initialDate.get(Calendar.MONTH) + 1) }
    var day by remember { mutableIntStateOf(initialDate.get(Calendar.DAY_OF_MONTH)) }
    val maxMonth = if (year == currentYear) currentMonth else 12
    val calendarMaxDay = remember(year, month) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    val maxDay = if (year == currentYear && month == currentMonth) {
        minOf(calendarMaxDay, currentDay)
    } else {
        calendarMaxDay
    }
    LaunchedEffect(maxMonth) {
        if (month > maxMonth) month = maxMonth
    }
    LaunchedEffect(maxDay) {
        if (day > maxDay) day = maxDay
    }
    GlassDialog(
        onDismissRequest=onDismiss,
        title={Text(stringResource(R.string.date_choose))},
        text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                GlassIconButton({year--},enabled=year>MIN_GIFT_YEAR){Icon(LiZhangIcons.ChevronLeft,stringResource(R.string.date_previous_year), tint = featureIconColor())}
                Text(stringResource(R.string.year_format,year),style=MaterialTheme.typography.titleMedium)
                GlassIconButton({year++},enabled=year<currentYear){Icon(LiZhangIcons.ChevronRight,stringResource(R.string.date_next_year), tint = featureIconColor())}
            }
            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items((1..maxMonth).toList()){value->GlassChip(value==month,{month=value},{Text(com.yangsong.lizhang.ui.mapper.displayMonth(value))})}}
            LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){items((1..maxDay).toList()){value->GlassChip(value==day,{day=value},{Text(stringResource(R.string.day_format,value))})}}
        }},
        confirmButton={GlassTextButton({val date=Calendar.getInstance().apply{set(year,month-1,day,0,0,0);set(Calendar.MILLISECOND,0)};onConfirm(date.timeInMillis)}){Text(stringResource(R.string.action_done))}},
        dismissButton={GlassTextButton(onDismiss){Text(stringResource(R.string.action_cancel))}},
    )
}

private const val MIN_GIFT_YEAR = 1900
