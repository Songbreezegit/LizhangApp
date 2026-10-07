package com.yangsong.lizhang.ui.component
import dev.chrisbanes.haze.HazeState
import com.yangsong.lizhang.ui.mapper.eventDisplayLabel
import androidx.annotation.DrawableRes
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yangsong.lizhang.R
import com.yangsong.lizhang.core.util.*
import com.yangsong.lizhang.domain.model.*
import com.yangsong.lizhang.ui.mapper.labelRes
import com.yangsong.lizhang.ui.navigation.AppDestination
import com.yangsong.lizhang.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AppTopBar(title:String,onBack:(()->Unit)?=null,action:(@Composable RowScope.()->Unit)?=null)=TopAppBar(title={Text(title,style=MaterialTheme.typography.titleLarge)},navigationIcon={if(onBack!=null)GlassIconButton(onBack){Icon(Icons.AutoMirrored.Outlined.ArrowBack,stringResource(R.string.action_back))}},actions={action?.invoke(this)},colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.Transparent))

@Composable
fun BottomNavBar(current: AppDestination, onNavigate: (AppDestination) -> Unit, modifier: Modifier = Modifier, hazeState: HazeState, enabled: Boolean = true) =
    NotchedBottomNavigation(current, onNavigate, modifier, hazeState, enabled)

@Composable fun PrimaryButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier,loading:Boolean=false,enabled:Boolean=true,icon:ImageVector?=null){GlassButton(onClick,modifier.heightIn(min=52.dp),enabled=enabled&&!loading,shape=RoundedCornerShape(GlassTokens.ControlRadius),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary,disabledContainerColor=if(loading)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,disabledContentColor=if(loading)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha=GlassTokens.DisabledAlpha))){if(loading)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=MaterialTheme.colorScheme.onPrimary)else{icon?.let{Icon(it,null);Spacer(Modifier.width(8.dp))};Text(text,fontWeight=FontWeight.SemiBold)}}}
@Composable fun SecondaryButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier,icon:ImageVector?=null){GlassButton(onClick,modifier.heightIn(min=52.dp),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.secondaryContainer,contentColor=MaterialTheme.colorScheme.onSecondaryContainer)){icon?.let{Icon(it,null);Spacer(Modifier.width(8.dp))};Text(text)}}
@Composable private fun visibleTextFieldColors()=OutlinedTextFieldDefaults.colors(focusedContainerColor=MaterialTheme.colorScheme.surfaceContainerLow,unfocusedContainerColor=MaterialTheme.colorScheme.surfaceContainerLow,focusedBorderColor=MaterialTheme.colorScheme.primary,unfocusedBorderColor=MaterialTheme.colorScheme.outlineVariant)
@Composable fun AppTextField(value:String,onValueChange:(String)->Unit,label:String,modifier:Modifier=Modifier,placeholder:String?=null,error:String?=null,leadingIcon:ImageVector?=null,readOnly:Boolean=false){OutlinedTextField(value,onValueChange,modifier.fillMaxWidth(),label={Text(label)},placeholder=placeholder?.let{{Text(it)}},leadingIcon=leadingIcon?.let{{Icon(it,null)}},isError=error!=null,supportingText=error?.let{{Text(it)}},readOnly=readOnly,singleLine=true,shape=RoundedCornerShape(18.dp),colors=visibleTextFieldColors())}
@Composable fun AppMultilineTextField(value:String,onValueChange:(String)->Unit,label:String,modifier:Modifier=Modifier,placeholder:String?=null){OutlinedTextField(value,onValueChange,modifier.fillMaxWidth(),label={Text(label)},placeholder=placeholder?.let{{Text(it)}},minLines=3,maxLines=5,shape=RoundedCornerShape(18.dp),colors=visibleTextFieldColors())}
@Composable fun AmountTextField(value:String,onValueChange:(String)->Unit,error:String?=null){OutlinedTextField(value,onValueChange,Modifier.fillMaxWidth(),label={Text(stringResource(R.string.field_amount))},prefix={Text("¥")},placeholder={Text(stringResource(R.string.field_amount_hint))},isError=error!=null,supportingText=error?.let{{Text(it)}},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),textStyle=MaterialTheme.typography.headlineMedium.copy(fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary),singleLine=true,shape=RoundedCornerShape(20.dp),colors=visibleTextFieldColors())}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DirectionSelector(selected:GiftDirection,onSelected:(GiftDirection)->Unit){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text(stringResource(R.string.field_direction),fontWeight=FontWeight.SemiBold);FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){GiftDirection.entries.forEach{d->GlassChip(d==selected,{onSelected(d)},{Text(stringResource(d.labelRes()))},leadingIcon={Icon(if(d==GiftDirection.RECEIVED)Icons.AutoMirrored.Outlined.CallReceived else Icons.AutoMirrored.Outlined.CallMade,null)},accent=if(d==GiftDirection.RECEIVED)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)}}}}
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventTypeSelector(selected: EventType, onSelected: (EventType) -> Unit,
    customEventName: String? = null, onCustomClick: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.field_event), fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EventType.entries.forEach { type ->
                GlassChip(type == selected && customEventName.isNullOrBlank(), { onSelected(type) }, { Text(stringResource(type.labelRes())) })
            }
            GlassChip(selected == EventType.OTHER && !customEventName.isNullOrBlank(), onCustomClick,
                { Text(customEventName ?: stringResource(R.string.event_custom_action)) })
        }
    }
}

@Composable fun AmountSummaryCard(label:String,amount:Long,modifier:Modifier=Modifier,tint:Color=MaterialTheme.colorScheme.primary){GlassCard(modifier,shape=RoundedCornerShape(GlassTokens.Radius)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(com.yangsong.lizhang.ui.mapper.displayAmount(amount),color=tint,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}}}
private val compoundSurnames=listOf("欧阳","司马","上官","诸葛","东方","皇甫","尉迟","公孙","慕容","司徒")
private fun contactSurname(name:String):String{val clean=name.trim();if(clean.isBlank())return "";compoundSurnames.firstOrNull{clean.startsWith(it)}?.let{return it};return clean.first().toString().uppercase()}
@Composable fun ContactAvatar(name:String,size:androidx.compose.ui.unit.Dp,modifier:Modifier=Modifier,tint:Color=MaterialTheme.colorScheme.secondary){Surface(modifier.size(size),shape=CircleShape,color=tint.copy(alpha=.10f)){Box(contentAlignment=Alignment.Center){val surname=contactSurname(name).ifEmpty { stringResource(R.string.contact_avatar_fallback) };Text(surname,color=tint,style=if(surname.length>1)MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)}}}
@Composable
fun GiftRecordListItem(item: GiftRecordWithContact, onClick: (() -> Unit)? = null) {
    val click = if (onClick == null) Modifier else Modifier.pressClickable(onClick = onClick)
    val tint = if (item.record.direction == GiftDirection.RECEIVED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val amount = stringResource(
        if (item.record.direction == GiftDirection.RECEIVED) R.string.amount_received else R.string.amount_given,
        com.yangsong.lizhang.ui.mapper.displayAmount(item.record.amountInCents),
    )
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.2f
    Row(click.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        ContactAvatar(item.contactName, 52.dp, tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.contactName, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(item.record.direction.labelRes()), Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall, color = tint)
            }
            Text(stringResource(R.string.record_event_date, item.record.eventDisplayLabel(), com.yangsong.lizhang.ui.mapper.displayDate(item.record.eventDate)),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (largeText) Text(amount, color = tint, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (!largeText) Text(amount, Modifier.widthIn(max = 150.dp), color = tint,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
@Composable
fun ContactListItem(summary: ContactLedgerSummary, enabled: Boolean = true, onClick: () -> Unit) {
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.2f
    val amountColor = if (summary.netInCents >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    Row(Modifier.fillMaxWidth().pressClickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        ContactAvatar(summary.contact.name, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(summary.contact.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge,
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(summary.contact.phone ?: summary.contact.relationship.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (largeText) {
                Text(stringResource(R.string.contact_net_amount, stringResource(R.string.contact_net), com.yangsong.lizhang.ui.mapper.displayAmount(summary.netInCents)),
                    color = amountColor, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!largeText) Column(horizontalAlignment = Alignment.End) {
            Text(com.yangsong.lizhang.ui.mapper.displayAmount(summary.netInCents), fontWeight = FontWeight.SemiBold, color = amountColor)
            Text(stringResource(R.string.contact_net), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun PageIllustration(@DrawableRes image:Int,modifier:Modifier=Modifier){Image(illustrationPainter(image),null,modifier.clip(RoundedCornerShape(GlassTokens.Radius)),contentScale=ContentScale.Fit)}
@Composable fun SectionHeader(title:String,action:String?=null,onAction:(()->Unit)?=null){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);if(action!=null&&onAction!=null)GlassTextButton(onAction){Text(action,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
@Composable fun EmptyState(title:String,description:String?=null,action:String?=null,onAction:(()->Unit)?=null,@DrawableRes image:Int?=null){Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)){if(image!=null)PageIllustration(image,Modifier.size(104.dp))else Icon(Icons.Outlined.FavoriteBorder,null,Modifier.size(44.dp),tint=MaterialTheme.colorScheme.primary);Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,textAlign=androidx.compose.ui.text.style.TextAlign.Center);description?.let{Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)};if(action!=null&&onAction!=null)PrimaryButton(action,onAction)}}
@Composable fun LoadingState(){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}}
@Composable fun ErrorState(onRetry:()->Unit){EmptyState(stringResource(R.string.load_failed),action=stringResource(R.string.action_retry),onAction=onRetry)}
@Composable fun CenteredSnackbarHost(hostState:SnackbarHostState){Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.Center){SnackbarHost(hostState,Modifier.widthIn(max=360.dp))}}
@Composable fun SettingsRow(icon:ImageVector,title:String,subtitle:String?=null,onClick:()->Unit,trailing:(@Composable ()->Unit)?=null,accent:Color=MaterialTheme.colorScheme.primary,modifier:Modifier=Modifier,diagnosticName:String?=null){Row(modifier.fillMaxWidth().pressClickable(accent = accent, diagnosticName=diagnosticName, onClick=onClick).heightIn(min=64.dp).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(14.dp),color=accent.copy(alpha=.10f)){Icon(icon,null,Modifier.padding(10.dp),tint=accent)};Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.SemiBold);subtitle?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}};trailing?.invoke()?:Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)}}
