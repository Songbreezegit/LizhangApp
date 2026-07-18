package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import com.yangsong.lizhang.domain.model.GiftRecordWithContact
import com.yangsong.lizhang.domain.repository.GiftRecordRepository
import java.util.Calendar
import kotlinx.coroutines.flow.*

data class MonthStat(val month:Int,val received:Long,val given:Long)
data class StatisticsUiState(
    val year:Int=Calendar.getInstance().get(Calendar.YEAR),
    val years:List<Int> = listOf(year),
    val received:Long=0,
    val given:Long=0,
    val months:List<MonthStat> = emptyList(),
    val events:List<Pair<EventType,Long>> = emptyList(),
    val contacts:List<Pair<String,Long>> = emptyList(),
    val isLoading:Boolean=true,
    val error:Boolean=false,
){val net get()=received-given}

class StatisticsViewModel(repository:GiftRecordRepository):ViewModel(){
    private val selectedYear=MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val uiState:StateFlow<StatisticsUiState> = combine(repository.observeRecent(Int.MAX_VALUE),selectedYear){records,year->aggregate(records,year)}
        .catch{emit(StatisticsUiState(isLoading=false,error=true))}
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),StatisticsUiState())
    fun selectYear(year:Int){selectedYear.value=year}
    companion object{
        fun factory(repository:GiftRecordRepository)=object:ViewModelProvider.Factory{
            @Suppress("UNCHECKED_CAST") override fun<T:ViewModel>create(modelClass:Class<T>)=StatisticsViewModel(repository)as T
        }
    }
}

private fun aggregate(records:List<GiftRecordWithContact>,year:Int):StatisticsUiState{
    fun recordYear(item:GiftRecordWithContact)=Calendar.getInstance().apply{timeInMillis=item.record.eventDate}.get(Calendar.YEAR)
    fun recordMonth(item:GiftRecordWithContact)=Calendar.getInstance().apply{timeInMillis=item.record.eventDate}.get(Calendar.MONTH)+1
    val filtered=records.filter{recordYear(it)==year}
    val received=filtered.filter{it.record.direction==GiftDirection.RECEIVED}.sumOf{it.record.amountInCents}
    val given=filtered.filter{it.record.direction==GiftDirection.GIVEN}.sumOf{it.record.amountInCents}
    val months=(1..12).map{month->
        val items=filtered.filter{recordMonth(it)==month}
        MonthStat(month,items.filter{it.record.direction==GiftDirection.RECEIVED}.sumOf{it.record.amountInCents},items.filter{it.record.direction==GiftDirection.GIVEN}.sumOf{it.record.amountInCents})
    }
    return StatisticsUiState(
        year=year,
        years=(records.map(::recordYear)+year).distinct().sortedDescending(),
        received=received,
        given=given,
        months=months,
        events=filtered.groupBy{it.record.eventType}.map{(type,items)->type to items.sumOf{it.record.amountInCents}}.sortedByDescending{it.second},
        contacts=filtered.groupBy{it.contactName}.map{(name,items)->name to items.sumOf{it.record.amountInCents}}.sortedByDescending{it.second}.take(5),
        isLoading=false,
    )
}
