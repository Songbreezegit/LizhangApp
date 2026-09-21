package com.yangsong.lizhang.ui.viewmodel

import com.yangsong.lizhang.domain.model.*
import com.yangsong.lizhang.domain.repository.ContactRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ContactBulkManagementViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    private fun TestScope.fixture(): Pair<ContactsViewModel, BulkFakeRepository> {
        val repository = BulkFakeRepository()
        val vm = ContactsViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        return vm to repository
    }

    @Test fun `进入管理不默认选择退出后清空`() = runTest(dispatcher) {
        val (vm, _) = fixture()
        vm.enterSelectionMode()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSelectionMode)
        assertTrue(vm.uiState.value.selectedContactIds.isEmpty())
        vm.toggleContact(1)
        vm.exitSelectionMode()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSelectionMode)
        assertTrue(vm.uiState.value.selectedContactIds.isEmpty())
    }

    @Test fun `按ID单选多选取消并忽略非法ID`() = runTest(dispatcher) {
        val (vm, _) = fixture()
        vm.enterSelectionMode()
        vm.toggleContact(1)
        vm.toggleContact(2)
        vm.toggleContact(999)
        advanceUntilIdle()
        assertEquals(setOf(1L, 2L), vm.uiState.value.selectedContactIds)
        vm.toggleContact(1)
        advanceUntilIdle()
        assertEquals(setOf(2L), vm.uiState.value.selectedContactIds)
    }

    @Test fun `搜索保留隐藏选择全选只追加当前结果取消全选清空全部`() = runTest(dispatcher) {
        val (vm, _) = fixture()
        vm.enterSelectionMode()
        vm.toggleContact(1)
        vm.updateQuery("乙")
        advanceUntilIdle()
        assertEquals(listOf(2L), vm.uiState.value.contacts.map { it.contact.id })
        assertEquals(setOf(1L), vm.uiState.value.selectedContactIds)
        vm.selectAllVisible()
        advanceUntilIdle()
        assertEquals(setOf(1L, 2L), vm.uiState.value.selectedContactIds)
        vm.clearSelection()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.selectedContactIds.isEmpty())
    }

    @Test fun `排序与新增刷新不改变既有选择`() = runTest(dispatcher) {
        val (vm, repo) = fixture()
        vm.enterSelectionMode()
        vm.toggleContact(2)
        vm.updateSort(ContactSort.NAME)
        repo.contacts.value = repo.contacts.value.reversed() + Contact(id = 4, name = "新测试")
        advanceUntilIdle()
        assertEquals(setOf(2L), vm.uiState.value.selectedContactIds)
        assertEquals(4, vm.uiState.value.contacts.size)
    }

    @Test fun `无选择或未预览不能删除取消确认不删除`() = runTest(dispatcher) {
        val (vm, repo) = fixture()
        vm.enterSelectionMode()
        vm.requestDelete()
        vm.confirmDelete()
        advanceUntilIdle()
        assertEquals(0, repo.previews)
        vm.toggleContact(1)
        vm.confirmDelete()
        assertEquals(0, repo.deletes)
        vm.requestDelete()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.deletePreview!!.contactCount)
        vm.dismissDelete()
        vm.confirmDelete()
        advanceUntilIdle()
        assertEquals(0, repo.deletes)
        assertEquals(setOf(1L), vm.uiState.value.selectedContactIds)
    }

    @Test fun `删除成功退出管理清空选择并提供真实结果`() = runTest(dispatcher) {
        val (vm, repo) = fixture()
        vm.enterSelectionMode()
        vm.selectAllVisible()
        vm.requestDelete()
        advanceUntilIdle()
        vm.confirmDelete()
        advanceUntilIdle()
        assertEquals(BulkDeleteResult(3, 0), vm.uiState.value.deleteResult)
        assertFalse(vm.uiState.value.isSelectionMode)
        assertTrue(vm.uiState.value.selectedContactIds.isEmpty())
        assertTrue(repo.contacts.value.isEmpty())
        assertTrue(vm.uiState.value.contacts.isEmpty())
    }

    @Test fun `预览与删除异常均保留选择并允许重试`() = runTest(dispatcher) {
        val (vm, repo) = fixture()
        vm.enterSelectionMode()
        vm.toggleContact(1)
        repo.failPreview = true
        vm.requestDelete()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.deleteFailed)
        assertEquals(setOf(1L), vm.uiState.value.selectedContactIds)
        repo.failPreview = false
        vm.requestDelete()
        advanceUntilIdle()
        repo.failDelete = true
        vm.confirmDelete()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.deleteFailed)
        assertTrue(vm.uiState.value.isSelectionMode)
        assertEquals(setOf(1L), vm.uiState.value.selectedContactIds)
        assertNull(vm.uiState.value.deleteResult)
        assertEquals(3, repo.contacts.value.size)
    }

    @Test fun `高风险删除必须明确勾选且快速点击只提交一次`() = runTest(dispatcher) {
        val (vm, repo) = fixture()
        repo.counts[1] = 2
        vm.enterSelectionMode()
        vm.toggleContact(1)
        vm.requestDelete()
        advanceUntilIdle()
        vm.confirmDelete()
        assertEquals(0, repo.deletes)
        vm.confirmCascade(true)
        repo.gate = CompletableDeferred()
        vm.confirmDelete()
        vm.confirmDelete()
        advanceUntilIdle()
        assertEquals(1, repo.deletes)
        assertTrue(vm.uiState.value.isDeleting)
        vm.clearSelection()
        vm.exitSelectionMode()
        advanceUntilIdle()
        assertEquals(setOf(1L), vm.uiState.value.selectedContactIds)
        repo.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(BulkDeleteResult(1, 2), vm.uiState.value.deleteResult)
    }

    @Test fun `确认后数量变化不删除并重新要求高风险确认`() = runTest(dispatcher) {
        val (vm, repo) = fixture()
        vm.enterSelectionMode()
        vm.toggleContact(1)
        vm.requestDelete()
        advanceUntilIdle()
        repo.counts[1] = 5
        vm.confirmDelete()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.deletePreviewChanged)
        assertEquals(5, vm.uiState.value.deletePreview!!.giftRecordCount)
        assertFalse(vm.uiState.value.cascadeConfirmed)
        assertEquals(3, repo.contacts.value.size)
        vm.confirmDelete()
        assertEquals(1, repo.deletes)
        vm.confirmCascade(true)
        vm.confirmDelete()
        advanceUntilIdle()
        assertEquals(BulkDeleteResult(1, 5), vm.uiState.value.deleteResult)
    }
}

private class BulkFakeRepository : ContactRepository {
    val contacts = MutableStateFlow(listOf(Contact(1, "甲测试"), Contact(2, "乙测试"), Contact(3, "丙测试")))
    val counts = mutableMapOf<Long, Int>()
    var previews = 0
    var deletes = 0
    var failPreview = false
    var failDelete = false
    var gate: CompletableDeferred<Unit>? = null
    private fun impact(ids: Set<Long>) = ContactDeletePreview(contacts.value.filter { it.id in ids }.associate { it.id to (counts[it.id] ?: 0) })
    override suspend fun previewDelete(ids: Set<Long>): ContactDeletePreview {
        previews++
        if (failPreview) error("测试统计异常")
        return impact(ids)
    }
    override suspend fun deleteContacts(ids: Set<Long>, confirmed: ContactDeletePreview): ContactBulkDeleteOutcome {
        deletes++
        gate?.await()
        if (failDelete) error("测试删除异常")
        val actual = impact(ids)
        if (actual != confirmed) return ContactBulkDeleteOutcome.Changed(actual)
        contacts.value = contacts.value.filterNot { it.id in ids }
        return ContactBulkDeleteOutcome.Deleted(BulkDeleteResult(actual.contactCount, actual.giftRecordCount))
    }
    override fun observeContacts(query: String) = contacts.map { list -> list.filter { it.name.contains(query) } }
    override fun observeContactSummaries(query: String) = observeContacts(query).map { list -> list.map { ContactLedgerSummary(it, 0, 0) } }
    override fun observeContact(contactId: Long) = contacts.map { list -> list.find { it.id == contactId } }
    override suspend fun create(contact: Contact) = error("此测试不单个新增")
    override suspend fun createAll(contacts: List<Contact>): ContactImportResult = error("此测试不导入")
    override suspend fun importDeviceContacts(selections: List<ContactImportSelection>): ContactImportResult = error("此测试不导入")
    override suspend fun update(contact: Contact) = Unit
    override suspend fun delete(contact: Contact) = error("批量删除不得逐条调用")
}
