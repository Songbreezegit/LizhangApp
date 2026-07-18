package com.yangsong.lizhang.ui.viewmodel
import androidx.lifecycle.*
import com.yangsong.lizhang.domain.model.*
import com.yangsong.lizhang.domain.repository.ContactRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
enum class ContactSort { RECENT, NAME }
data class ContactsUiState(val query:String="", val sort:ContactSort=ContactSort.RECENT, val contacts:List<ContactLedgerSummary> = emptyList(), val isLoading:Boolean=true, val error:Boolean=false)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ContactsViewModel(private val repository:ContactRepository):ViewModel(){ private val query=MutableStateFlow(""); private val sort=MutableStateFlow(ContactSort.RECENT); val uiState=combine(query,sort){q,s->q to s}.flatMapLatest{(q,s)->repository.observeContactSummaries(q).map{ list->ContactsUiState(q,s,if(s==ContactSort.NAME)list.sortedBy{it.contact.name}else list,isLoading=false)}}.catch{emit(ContactsUiState(isLoading=false,error=true))}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),ContactsUiState()); fun updateQuery(v:String){query.value=v}; fun updateSort(v:ContactSort){sort.value=v}; fun save(c:Contact)=viewModelScope.launch{if(c.id==0L)repository.create(c)else repository.update(c)}; companion object{fun factory(r:ContactRepository)=object:ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST") override fun<T:ViewModel>create(m:Class<T>)=ContactsViewModel(r)as T}}}
