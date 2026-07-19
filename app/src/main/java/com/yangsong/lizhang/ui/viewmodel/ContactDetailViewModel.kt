package com.yangsong.lizhang.ui.viewmodel
import androidx.lifecycle.*
import com.yangsong.lizhang.domain.model.*
import com.yangsong.lizhang.domain.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
data class ContactDetailUiState(val contact:Contact?=null,val records:List<GiftRecord> = emptyList(),val received:Long=0,val given:Long=0,val isLoading:Boolean=true,val error:Boolean=false){val net get()=received-given}
class ContactDetailViewModel(id:Long,c:ContactRepository,private val g:GiftRecordRepository):ViewModel(){val uiState=combine(c.observeContact(id),g.observeByContact(id)){contact,records->ContactDetailUiState(contact,records,records.filter{it.direction==GiftDirection.RECEIVED}.sumOf{it.amountInCents},records.filter{it.direction==GiftDirection.GIVEN}.sumOf{it.amountInCents},false)}.catch{emit(ContactDetailUiState(isLoading=false,error=true))}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),ContactDetailUiState());fun deleteRecord(r:GiftRecord)=viewModelScope.launch{g.delete(r)};companion object{fun factory(id:Long,c:ContactRepository,g:GiftRecordRepository)=object:ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST")override fun<T:ViewModel>create(modelClass:Class<T>)=ContactDetailViewModel(id,c,g)as T}}}
