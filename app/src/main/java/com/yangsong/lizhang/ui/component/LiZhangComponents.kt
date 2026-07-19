package com.yangsong.lizhang.ui.component
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
@Composable fun AppTopBar(title:String,onBack:(()->Unit)?=null,action:(@Composable RowScope.()->Unit)?=null)=TopAppBar(title={Text(title,style=MaterialTheme.typography.titleLarge)},navigationIcon={if(onBack!=null)IconButton(onBack){Icon(Icons.AutoMirrored.Outlined.ArrowBack,stringResource(R.string.action_back))}},actions={action?.invoke(this)},colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))

private data class NavItem(val destination:AppDestination,val label:Int,val icon:ImageVector)
private val navItems=listOf(NavItem(AppDestination.Home,R.string.nav_home,Icons.Outlined.Home),NavItem(AppDestination.Contacts,R.string.nav_contacts,Icons.Outlined.PersonOutline),NavItem(AppDestination.AddGift,R.string.nav_add_gift,Icons.Outlined.EditNote),NavItem(AppDestination.Settings,R.string.nav_settings,Icons.Outlined.AccountCircle))
@Composable fun BottomNavBar(current:AppDestination,onNavigate:(AppDestination)->Unit){
    val dark=MaterialTheme.colorScheme.background.luminance()<.5f
    val glassTop=if(dark)MaterialTheme.colorScheme.surface else Color.White
    val glassBottom=if(dark)MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF8F6F2)
    val glassShape=RoundedCornerShape(30.dp)
    Box(Modifier.fillMaxWidth().navigationBarsPadding()){
        Row(
            Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=7.dp).height(78.dp)
                .shadow(10.dp,glassShape,clip=false)
                .graphicsLayer{alpha=if(dark).90f else .88f;compositingStrategy=CompositingStrategy.Offscreen}
                .clip(glassShape).background(Brush.verticalGradient(listOf(glassTop,glassBottom)))
                .border(1.dp,if(dark)Color.White.copy(alpha=.12f)else Color.White.copy(alpha=.9f),glassShape)
                .padding(horizontal=6.dp),
            verticalAlignment=Alignment.CenterVertically,
        ){
            navItems.forEach{item->
                val selected=current==item.destination
                val tint=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(role=Role.Tab){onNavigate(item.destination)}.semantics{this.selected=selected;role=Role.Tab},
                    horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.Center,
                ){
                    Box(
                        Modifier.width(62.dp).height(31.dp).background(if(selected)MaterialTheme.colorScheme.primaryContainer.copy(alpha=.78f)else Color.Transparent,RoundedCornerShape(18.dp)),
                        contentAlignment=Alignment.Center,
                    ){Icon(item.icon,null,Modifier.size(23.dp),tint=tint)}
                    Spacer(Modifier.height(3.dp))
                    Text(stringResource(item.label),color=tint,style=MaterialTheme.typography.labelMedium,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable fun PrimaryButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier,loading:Boolean=false,enabled:Boolean=true,icon:ImageVector?=null){Button(onClick,modifier.heightIn(min=52.dp),enabled=enabled&&!loading,shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.primary)){if(loading)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=MaterialTheme.colorScheme.onPrimary)else{icon?.let{Icon(it,null);Spacer(Modifier.width(8.dp))};Text(text,fontWeight=FontWeight.SemiBold)}}}
@Composable fun SecondaryButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier,icon:ImageVector?=null){OutlinedButton(onClick,modifier.heightIn(min=52.dp),shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)){icon?.let{Icon(it,null);Spacer(Modifier.width(8.dp))};Text(text)}}
@Composable fun AppTextField(value:String,onValueChange:(String)->Unit,label:String,modifier:Modifier=Modifier,placeholder:String?=null,error:String?=null,leadingIcon:ImageVector?=null,readOnly:Boolean=false){OutlinedTextField(value,onValueChange,modifier.fillMaxWidth(),label={Text(label)},placeholder=placeholder?.let{{Text(it)}},leadingIcon=leadingIcon?.let{{Icon(it,null)}},isError=error!=null,supportingText=error?.let{{Text(it)}},readOnly=readOnly,singleLine=true,shape=RoundedCornerShape(18.dp),colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedBorderColor=Color.Transparent))}
@Composable fun AppMultilineTextField(value:String,onValueChange:(String)->Unit,label:String,modifier:Modifier=Modifier,placeholder:String?=null){OutlinedTextField(value,onValueChange,modifier.fillMaxWidth(),label={Text(label)},placeholder=placeholder?.let{{Text(it)}},minLines=3,maxLines=5,shape=RoundedCornerShape(18.dp),colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedBorderColor=Color.Transparent))}
@Composable fun AmountTextField(value:String,onValueChange:(String)->Unit,error:String?=null){OutlinedTextField(value,onValueChange,Modifier.fillMaxWidth(),label={Text(stringResource(R.string.field_amount))},prefix={Text("¥")},placeholder={Text(stringResource(R.string.field_amount_hint))},isError=error!=null,supportingText=error?.let{{Text(it)}},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),textStyle=MaterialTheme.typography.headlineMedium.copy(fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary),singleLine=true,shape=RoundedCornerShape(20.dp),colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedBorderColor=Color.Transparent))}

@Composable fun DirectionSelector(selected:GiftDirection,onSelected:(GiftDirection)->Unit){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text(stringResource(R.string.field_direction),fontWeight=FontWeight.SemiBold);Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){GiftDirection.entries.forEach{d->val color=if(d==GiftDirection.RECEIVED)CoralContainer else MintContainer;FilterChip(d==selected,{onSelected(d)},{Text(stringResource(d.labelRes()))},leadingIcon={Icon(if(d==GiftDirection.RECEIVED)Icons.AutoMirrored.Outlined.CallReceived else Icons.AutoMirrored.Outlined.CallMade,null)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=color,selectedLabelColor=if(d==GiftDirection.RECEIVED)CoralStrong else MintPrimary),border=FilterChipDefaults.filterChipBorder(enabled=true,selected=d==selected,borderColor=Color.Transparent,selectedBorderColor=Color.Transparent))}}}}
@OptIn(ExperimentalLayoutApi::class) @Composable fun EventTypeSelector(selected:EventType,onSelected:(EventType)->Unit){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text(stringResource(R.string.field_event),fontWeight=FontWeight.SemiBold);FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){EventType.entries.forEach{type->FilterChip(type==selected,{onSelected(type)},{Text(stringResource(type.labelRes()))},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=ApricotContainer),border=FilterChipDefaults.filterChipBorder(enabled=true,selected=type==selected,borderColor=Color.Transparent,selectedBorderColor=Color.Transparent))}}}}

@Composable fun AmountSummaryCard(label:String,amount:Long,modifier:Modifier=Modifier,tint:Color=MaterialTheme.colorScheme.primary){Card(modifier,shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(2.dp)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(CurrencyFormatter.formatCents(amount),color=tint,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}}}
private val compoundSurnames=listOf("欧阳","司马","上官","诸葛","东方","皇甫","尉迟","公孙","慕容","司徒")
private fun contactSurname(name:String):String{val clean=name.trim();if(clean.isBlank())return "人";compoundSurnames.firstOrNull{clean.startsWith(it)}?.let{return it};return clean.first().toString().uppercase()}
@Composable fun ContactAvatar(name:String,size:androidx.compose.ui.unit.Dp,modifier:Modifier=Modifier){Surface(modifier.size(size),shape=CircleShape,color=AvatarBackground){Box(contentAlignment=Alignment.Center){val surname=contactSurname(name);Text(surname,color=AvatarText,style=if(surname.length>1)MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)}}}
@Composable fun GiftRecordListItem(item:GiftRecordWithContact,onClick:(()->Unit)?=null){val click=if(onClick==null)Modifier else Modifier.clickable(onClick=onClick);Row(click.fillMaxWidth().padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically){ContactAvatar(item.contactName,52.dp);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Row(verticalAlignment=Alignment.CenterVertically){Text(item.contactName,style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.Bold);Spacer(Modifier.width(8.dp));Surface(shape=RoundedCornerShape(6.dp),color=if(item.record.direction==GiftDirection.RECEIVED)CoralContainer else MintContainer){Text(stringResource(item.record.direction.labelRes()),Modifier.padding(horizontal=6.dp,vertical=2.dp),style=MaterialTheme.typography.labelSmall,color=if(item.record.direction==GiftDirection.RECEIVED)CoralStrong else MintPrimary)}};Text("${stringResource(item.record.eventType.labelRes())} · ${DateFormatter.format(item.record.eventDate)}",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)};Text((if(item.record.direction==GiftDirection.RECEIVED)"+"else"-")+CurrencyFormatter.formatCents(item.record.amountInCents),color=if(item.record.direction==GiftDirection.RECEIVED)CoralStrong else MintPrimary,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}}
@Composable fun ContactListItem(summary:ContactLedgerSummary,onClick:()->Unit){Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically){ContactAvatar(summary.contact.name,56.dp);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(summary.contact.name,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.bodyLarge);Text(summary.contact.phone?:summary.contact.relationship.orEmpty(),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)};Column(horizontalAlignment=Alignment.End){Text(CurrencyFormatter.formatCents(summary.netInCents),fontWeight=FontWeight.Bold,color=if(summary.netInCents>=0)CoralStrong else MintPrimary);Text(stringResource(R.string.contact_net),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelSmall)};Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)}}

@Composable fun PageIllustration(@DrawableRes image:Int,modifier:Modifier=Modifier){Image(painterResource(image),null,modifier.clip(RoundedCornerShape(24.dp)),contentScale=ContentScale.Fit)}
@Composable fun SectionHeader(title:String,action:String?=null,onAction:(()->Unit)?=null){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);if(action!=null&&onAction!=null)TextButton(onAction){Text(action,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
@Composable fun EmptyState(title:String,description:String?=null,action:String?=null,onAction:(()->Unit)?=null,@DrawableRes image:Int?=null){Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)){if(image!=null)PageIllustration(image,Modifier.size(150.dp))else Icon(Icons.Outlined.FavoriteBorder,null,Modifier.size(44.dp),tint=MaterialTheme.colorScheme.primary);Text(title,style=MaterialTheme.typography.titleMedium);description?.let{Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant)};if(action!=null&&onAction!=null)PrimaryButton(action,onAction)}}
@Composable fun LoadingState(){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}}
@Composable fun ErrorState(onRetry:()->Unit){EmptyState(stringResource(R.string.load_failed),action=stringResource(R.string.action_retry),onAction=onRetry)}
@Composable fun SettingsRow(icon:ImageVector,title:String,subtitle:String?=null,onClick:()->Unit,trailing:(@Composable ()->Unit)?=null){Row(Modifier.fillMaxWidth().clickable(onClick=onClick).heightIn(min=64.dp).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(14.dp),color=CoralContainer){Icon(icon,null,Modifier.padding(10.dp),tint=CoralStrong)};Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.SemiBold);subtitle?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}};trailing?.invoke()?:Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)}}
