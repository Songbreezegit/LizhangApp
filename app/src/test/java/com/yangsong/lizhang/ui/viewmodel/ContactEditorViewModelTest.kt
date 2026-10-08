package com.yangsong.lizhang.ui.viewmodel

import com.yangsong.lizhang.core.common.NavigationConstants
import com.yangsong.lizhang.domain.model.Contact
import com.yangsong.lizhang.domain.model.ContactLedgerSummary
import com.yangsong.lizhang.domain.repository.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `联系人内容变更后标记未保存且保存成功后清除标记`() = runTest(dispatcher) {
        val repository = ContactEditorFakeRepository()
        val viewModel = ContactEditorViewModel(
            contactId = NavigationConstants.NEW_CONTACT_ID,
            repository = repository,
        )

        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.updateName("王阿姨")

        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        viewModel.save()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals(1L, viewModel.uiState.value.savedContactId)
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        assertTrue(repository.created)
    }

    @Test
    fun `新建联系人连续点击仅创建一次并保留实际返回编号`() = runTest(dispatcher) {
        val repository = ContactEditorFakeRepository().apply { createdId = 42L }
        val viewModel = ContactEditorViewModel(NavigationConstants.NEW_CONTACT_ID, repository)
        viewModel.updateName("测试联系人")

        repeat(5) { viewModel.save() }
        assertTrue(viewModel.uiState.value.isSaving)
        assertNull(viewModel.uiState.value.savedContactId)
        advanceUntilIdle()
        viewModel.save()
        advanceUntilIdle()

        assertEquals(1, repository.createCount)
        assertEquals(42L, viewModel.uiState.value.savedContactId)
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `联系人保存失败不提供返回编号且重试成功后才提供`() = runTest(dispatcher) {
        val repository = ContactEditorFakeRepository().apply { failCreate = true; createdId = 42L }
        val viewModel = ContactEditorViewModel(NavigationConstants.NEW_CONTACT_ID, repository)
        viewModel.updateName("测试联系人")
        viewModel.updateNotes("模拟备注")

        viewModel.save()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNull(viewModel.uiState.value.savedContactId)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        assertEquals("模拟备注", viewModel.uiState.value.notes)
        repository.failCreate = false
        viewModel.save()
        advanceUntilIdle()

        assertEquals(42L, viewModel.uiState.value.savedContactId)
        assertFalse(viewModel.uiState.value.operationFailed)
    }
}

private class ContactEditorFakeRepository : ContactRepository {
    override suspend fun previewDelete(ids: Set<Long>): com.yangsong.lizhang.domain.model.ContactDeletePreview = error("此测试不执行批量删除")
    override suspend fun deleteContacts(ids: Set<Long>, confirmed: com.yangsong.lizhang.domain.model.ContactDeletePreview): com.yangsong.lizhang.domain.model.ContactBulkDeleteOutcome = error("此测试不执行批量删除")
    override suspend fun importDeviceContacts(selections: List<com.yangsong.lizhang.domain.model.ContactImportSelection>): com.yangsong.lizhang.domain.model.ContactImportResult = error("此测试不执行通讯录导入")
    override suspend fun createAll(contacts: List<Contact>): com.yangsong.lizhang.domain.model.ContactImportResult = error("此测试不执行通讯录导入")
    var created = false
    var createdId = 1L
    var createCount = 0
    var failCreate = false

    override fun observeContacts(query: String): Flow<List<Contact>> = flowOf(emptyList())

    override fun observeContactSummaries(query: String): Flow<List<ContactLedgerSummary>> =
        flowOf(emptyList())

    override fun observeContact(contactId: Long): Flow<Contact?> = flowOf(null)

    override suspend fun create(contact: Contact): Long {
        createCount++
        if (failCreate) error("模拟联系人保存失败")
        created = true
        return createdId
    }

    override suspend fun update(contact: Contact) = Unit

    override suspend fun delete(contact: Contact) = Unit
}
