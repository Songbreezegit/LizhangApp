package com.yangsong.lizhang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.yangsong.lizhang.core.common.NavigationConstants
import com.yangsong.lizhang.domain.repository.GuidePracticeRepository
import com.yangsong.lizhang.domain.model.EventType
import com.yangsong.lizhang.domain.model.GiftDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class GuidePracticeStep {
    CONTACTS, CONTACT_EDITOR_EMPTY, CONTACT_EDITOR_FILLED, CONTACT_DETAIL,
    GIFT_EDITOR_EMPTY, GIFT_EDITOR_FILLED, GIFT_DETAIL, DELETE_GIFT_CONFIRM,
    CONTACT_AFTER_GIFT_DELETE, CONTACT_DELETE, DELETE_CONTACT_CONFIRM, CONTACTS_CLEAN;
}

data class GuidePracticeUiState(
    val step: GuidePracticeStep = GuidePracticeStep.CONTACTS,
    val busy: Boolean = false,
    val closed: Boolean = false,
    val error: String? = null,
)

/** 原业务页面及 ViewModel 的分步演示；它们的仓储全部来自独立的内存练习空间。 */
class GuidePracticeViewModel(private val store: GuidePracticeRepository) : ViewModel() {
    private val children = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
    private val mutableState = MutableStateFlow(GuidePracticeUiState())
    val uiState = mutableState.asStateFlow()

    private fun <T : ViewModel> child(key: String, type: Class<T>, factory: ViewModelProvider.Factory): T =
        ViewModelProvider(children, factory)[key, type]

    val contacts: ContactsViewModel by lazy {
        child("练习联系人列表", ContactsViewModel::class.java, ContactsViewModel.factory(store.contactRepository))
    }
    val contactEditor: ContactEditorViewModel by lazy {
        child("练习新建联系人", ContactEditorViewModel::class.java,
            ContactEditorViewModel.factory(NavigationConstants.NEW_CONTACT_ID, store.contactRepository))
    }
    val contactDetail: ContactDetailViewModel by lazy {
        child("练习联系人详情", ContactDetailViewModel::class.java,
            ContactDetailViewModel.factory(store.contactId, store.contactRepository, store.giftRecordRepository))
    }
    val giftEditor: GiftEditorViewModel by lazy {
        child("练习礼金编辑", GiftEditorViewModel::class.java,
            GiftEditorViewModel.factory(store.giftRecordRepository, store.contactRepository, initialContactId = store.contactId))
    }
    val giftDetail: GiftRecordDetailViewModel by lazy {
        child("练习礼金详情", GiftRecordDetailViewModel::class.java,
            GiftRecordDetailViewModel.factory(store.recordId, store.giftRecordRepository))
    }
    val contactDeletion: ContactEditorViewModel by lazy {
        child("练习联系人删除", ContactEditorViewModel::class.java,
            ContactEditorViewModel.factory(store.contactId, store.contactRepository))
    }

    /** 只有用户按下一步才发起填写、保存、跳转或删除，不使用计时推进。 */
    fun next(expected: GuidePracticeStep = mutableState.value.step) {
        val current = mutableState.value
        if (current.closed || current.busy || current.step != expected) return
        when (current.step) {
            GuidePracticeStep.CONTACTS -> move(GuidePracticeStep.CONTACT_EDITOR_EMPTY)
            GuidePracticeStep.CONTACT_EDITOR_EMPTY -> {
                contactEditor.updateName("示例小礼")
                contactEditor.updateRelationship("朋友")
                contactEditor.updateNotes("功能引导练习")
                move(GuidePracticeStep.CONTACT_EDITOR_FILLED)
            }
            GuidePracticeStep.CONTACT_EDITOR_FILLED -> {
                beginOperation()
                contactEditor.save()
                viewModelScope.launch {
                    val result = contactEditor.uiState.first { it.isSaved || it.operationFailed || it.nameError }
                    if (!result.isSaved) fail(current.step)
                }
            }
            GuidePracticeStep.CONTACT_DETAIL -> move(GuidePracticeStep.GIFT_EDITOR_EMPTY)
            GuidePracticeStep.GIFT_EDITOR_EMPTY -> {
                giftEditor.update { it.copy(contactId = store.contactId, amount = "200",
                    direction = GiftDirection.RECEIVED, eventType = EventType.BIRTHDAY, notes = "生日礼金示例") }
                move(GuidePracticeStep.GIFT_EDITOR_FILLED)
            }
            GuidePracticeStep.GIFT_EDITOR_FILLED -> {
                beginOperation()
                giftEditor.save()
                viewModelScope.launch {
                    val result = giftEditor.uiState.first { it.isSaved || it.operationFailed || it.validationError != null }
                    if (!result.isSaved) fail(current.step)
                }
            }
            GuidePracticeStep.GIFT_DETAIL -> move(GuidePracticeStep.DELETE_GIFT_CONFIRM)
            GuidePracticeStep.DELETE_GIFT_CONFIRM -> {
                if (giftDetail.uiState.value.isLoading || giftDetail.uiState.value.item == null) return
                beginOperation()
                giftDetail.delete()
                viewModelScope.launch {
                    val result = giftDetail.uiState.first { it.isDeleted || it.deleteFailed }
                    if (!result.isDeleted) fail(current.step)
                }
            }
            GuidePracticeStep.CONTACT_AFTER_GIFT_DELETE -> move(GuidePracticeStep.CONTACT_DELETE)
            GuidePracticeStep.CONTACT_DELETE -> {
                if (!contactDeletion.uiState.value.isLoading) move(GuidePracticeStep.DELETE_CONTACT_CONFIRM)
            }
            GuidePracticeStep.DELETE_CONTACT_CONFIRM -> {
                if (contactDeletion.uiState.value.isLoading || contactDeletion.uiState.value.loadFailed) return
                beginOperation()
                contactDeletion.delete()
                viewModelScope.launch {
                    val result = contactDeletion.uiState.first { it.isDeleted || it.operationFailed }
                    if (!result.isDeleted) fail(current.step)
                }
            }
            GuidePracticeStep.CONTACTS_CLEAN -> Unit
        }
    }

    // 承接真实页面原有的成功反馈；迟到或重复回调不能跳过当前讲解。
    fun contactSaved() = completeOperation(GuidePracticeStep.CONTACT_EDITOR_FILLED, GuidePracticeStep.CONTACT_DETAIL,
        contactEditor.uiState.value.isSaved)
    fun giftSaved() = completeOperation(GuidePracticeStep.GIFT_EDITOR_FILLED, GuidePracticeStep.GIFT_DETAIL,
        giftEditor.uiState.value.isSaved)
    fun giftDeleted() = completeOperation(GuidePracticeStep.DELETE_GIFT_CONFIRM, GuidePracticeStep.CONTACT_AFTER_GIFT_DELETE,
        giftDetail.uiState.value.isDeleted)
    fun contactDeleted() = completeOperation(GuidePracticeStep.DELETE_CONTACT_CONFIRM, GuidePracticeStep.CONTACTS_CLEAN,
        contactDeletion.uiState.value.isDeleted)

    fun cancelDeletion() {
        if (mutableState.value.busy || mutableState.value.closed) return
        when (mutableState.value.step) {
            GuidePracticeStep.DELETE_GIFT_CONFIRM -> move(GuidePracticeStep.GIFT_DETAIL)
            GuidePracticeStep.DELETE_CONTACT_CONFIRM -> move(GuidePracticeStep.CONTACT_DELETE)
            else -> Unit
        }
    }

    private fun move(step: GuidePracticeStep) { mutableState.value = GuidePracticeUiState(step = step) }
    private fun beginOperation() { mutableState.value = mutableState.value.copy(busy = true, error = null) }
    private fun fail(expected: GuidePracticeStep) {
        val current = mutableState.value
        if (!current.closed && current.step == expected && current.busy) {
            mutableState.value = current.copy(busy = false, error = "本步演示没有完成，请重试或退出练习。")
        }
    }
    private fun completeOperation(expected: GuidePracticeStep, next: GuidePracticeStep, succeeded: Boolean) {
        val current = mutableState.value
        // 以原 ViewModel 的成功事实收尾，重试的旧失败反馈不能吞掉随后到达的成功。
        if (!current.closed && current.step == expected && succeeded) move(next)
    }

    fun clear() {
        if (mutableState.value.closed) return
        mutableState.value = mutableState.value.copy(closed = true, busy = false)
        children.viewModelStore.clear()
        store.clear()
    }

    override fun onCleared() { clear(); super.onCleared() }

    companion object {
        fun factory(store: () -> GuidePracticeRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = GuidePracticeViewModel(store()) as T
        }
    }
}
