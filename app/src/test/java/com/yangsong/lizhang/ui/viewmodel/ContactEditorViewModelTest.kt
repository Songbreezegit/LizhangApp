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
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        assertTrue(repository.created)
    }
}

private class ContactEditorFakeRepository : ContactRepository {
    var created = false

    override fun observeContacts(query: String): Flow<List<Contact>> = flowOf(emptyList())

    override fun observeContactSummaries(query: String): Flow<List<ContactLedgerSummary>> =
        flowOf(emptyList())

    override fun observeContact(contactId: Long): Flow<Contact?> = flowOf(null)

    override suspend fun create(contact: Contact): Long {
        created = true
        return 1
    }

    override suspend fun update(contact: Contact) = Unit

    override suspend fun delete(contact: Contact) = Unit
}
