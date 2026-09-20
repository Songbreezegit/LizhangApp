package com.yangsong.lizhang.ui.viewmodel

import com.yangsong.lizhang.domain.model.*
import com.yangsong.lizhang.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val records = listOf(
        DeviceContact(1, "测试甲", "13800000000"),
        DeviceContact(2, "测试乙", "+1 (202) 555-0123"),
        DeviceContact(3, "测试丙", "13900000000"),
    )
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun `授权前和拒绝后不读取`() = runTest(dispatcher) {
        var reads = 0
        val vm = ContactImportViewModel(DeviceContactRepository { reads++; records }, ImportFakeRepository(), dispatcher)
        vm.load()
        vm.permissionChanged(ContactPermissionState.DENIED)
        vm.permissionChanged(ContactPermissionState.BLOCKED)
        advanceUntilIdle()
        assertEquals(0, reads)
        assertEquals(ContactPermissionState.BLOCKED, vm.uiState.value.permissionState)
    }

    @Test fun `同名候选默认未选但可手选并传递全部候选统计`() = runTest(dispatcher) {
        val repository = ImportFakeRepository(listOf(Contact(name = "测试甲", phone = null), Contact(name = "测试乙", phone = "13600000000")))
        val vm = ContactImportViewModel(DeviceContactRepository { records }, repository, dispatcher)
        vm.permissionChanged(ContactPermissionState.GRANTED)
        advanceUntilIdle()
        assertEquals(setOf("13900000000"), vm.uiState.value.selectedKeys)
        assertEquals(2, vm.uiState.value.contacts.count { it.status == ContactImportStatus.POSSIBLE_DUPLICATE })
        vm.toggle("13800000000")
        vm.importSelected()
        advanceUntilIdle()
        assertEquals(3, repository.selections.size)
        assertTrue(repository.selections.first { it.contact.phone == "13800000000" }.allowPossibleDuplicate)
        assertFalse(repository.selections.first { it.contact.phone == "+12025550123" }.selected)
        assertFalse(repository.selections.first { it.contact.phone == "13900000000" }.allowPossibleDuplicate)
    }

    @Test fun `全选作为主动选择包括同名候选且取消全选全部清空`() = runTest(dispatcher) {
        val vm = ContactImportViewModel(DeviceContactRepository { records }, ImportFakeRepository(listOf(Contact(name = "测试甲"))), dispatcher)
        vm.permissionChanged(ContactPermissionState.GRANTED)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.selectedKeys.size)
        vm.selectAll(true)
        assertEquals(3, vm.uiState.value.selectedKeys.size)
        vm.selectAll(false)
        assertTrue(vm.uiState.value.selectedKeys.isEmpty())
    }
    @Test fun `同号异名禁选其余默认选中支持全选取消`() = runTest(dispatcher) {
        val repository = ImportFakeRepository(listOf(Contact(name = "不同姓名", phone = "+86 13800000000")))
        val vm = ContactImportViewModel(DeviceContactRepository { records }, repository, dispatcher)
        vm.permissionChanged(ContactPermissionState.GRANTED)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.selectedKeys.size)
        vm.toggle("13800000000")
        assertFalse("13800000000" in vm.uiState.value.selectedKeys)
        vm.selectAll(false)
        assertTrue(vm.uiState.value.selectedKeys.isEmpty())
        vm.selectAll(true)
        assertEquals(2, vm.uiState.value.selectedKeys.size)
        vm.toggle("13900000000")
        assertEquals(setOf("+12025550123"), vm.uiState.value.selectedKeys)
    }
    @Test fun `姓名号码搜索保留隐藏选择`() = runTest(dispatcher) {
        val vm = ContactImportViewModel(DeviceContactRepository { records }, ImportFakeRepository(), dispatcher)
        vm.permissionChanged(ContactPermissionState.GRANTED)
        advanceUntilIdle()
        vm.updateQuery("测试乙")
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.visibleContacts.size)
        vm.updateQuery("+1 (202) 555-0123")
        advanceUntilIdle()
        assertEquals("测试乙", vm.uiState.value.visibleContacts.single().contact.name)
        assertEquals(3, vm.uiState.value.selectedKeys.size)
        vm.updateQuery("不存在")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.visibleContacts.isEmpty())
    }
    @Test fun `完整号码批量提交且快速点击只调用一次并显示仓库统计`() = runTest(dispatcher) {
        val repository = ImportFakeRepository().apply { result = ContactImportResult(2, 1) }
        val vm = ContactImportViewModel(DeviceContactRepository { records }, repository, dispatcher)
        vm.permissionChanged(ContactPermissionState.GRANTED)
        advanceUntilIdle()
        vm.importSelected()
        vm.importSelected()
        assertTrue(vm.uiState.value.isImporting)
        advanceUntilIdle()
        vm.importSelected()
        assertEquals(1, repository.calls)
        assertEquals(ContactImportResult(2, 1), vm.uiState.value.importResult)
        assertTrue(repository.received.all { it.relationship == null && it.notes == null && !it.phone.orEmpty().contains('*') })
    }
    @Test fun `空通讯录和全部存在不会提交`() = runTest(dispatcher) {
        for (rows in listOf(emptyList(), records)) {
            val repository = ImportFakeRepository(records.map { Contact(name = it.name, phone = it.phone) })
            val vm = ContactImportViewModel(DeviceContactRepository { rows }, repository, dispatcher)
            vm.permissionChanged(ContactPermissionState.GRANTED)
            advanceUntilIdle()
            vm.importSelected()
            assertTrue(vm.uiState.value.selectedKeys.isEmpty())
            assertEquals(0, repository.calls)
        }
    }
    @Test fun `读取失败可重试撤销权限清空候选`() = runTest(dispatcher) {
        var fail = true
        val vm = ContactImportViewModel(DeviceContactRepository { if (fail) error("测试异常") else records }, ImportFakeRepository(), dispatcher)
        vm.permissionChanged(ContactPermissionState.GRANTED)
        advanceUntilIdle()
        assertEquals(ContactImportError.READ, vm.uiState.value.error)
        fail = false
        vm.load()
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.contacts.size)
        vm.permissionChanged(ContactPermissionState.DENIED)
        assertTrue(vm.uiState.value.contacts.isEmpty())
        assertTrue(vm.uiState.value.selectedKeys.isEmpty())
    }
    @Test fun `写入失败保留选择允许重试`() = runTest(dispatcher) {
        val repository = ImportFakeRepository().apply { fail = true }
        val vm = ContactImportViewModel(DeviceContactRepository { records }, repository, dispatcher)
        vm.permissionChanged(ContactPermissionState.GRANTED)
        advanceUntilIdle()
        vm.importSelected()
        advanceUntilIdle()
        assertEquals(ContactImportError.WRITE, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isImporting)
        assertEquals(3, vm.uiState.value.selectedKeys.size)
        repository.fail = false
        vm.importSelected()
        advanceUntilIdle()
        assertEquals(ContactImportResult(3, 0), vm.uiState.value.importResult)
    }

    @Test fun `读取中权限失效回到权限提示`() = runTest(dispatcher) {
        val vm = ContactImportViewModel(DeviceContactRepository { throw SecurityException("测试撤销") }, ImportFakeRepository(), dispatcher)
        vm.permissionChanged(ContactPermissionState.GRANTED)
        advanceUntilIdle()
        assertEquals(ContactPermissionState.DENIED, vm.uiState.value.permissionState)
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.contacts.isEmpty())
    }

    @Test fun `三千候选可以清洗搜索和批量选择`() = runTest(dispatcher) {
        val rows = (0 until 3000).map { DeviceContact(it.toLong(), "测试$it", "+1202555${it.toString().padStart(4, '0')}") }
        val vm = ContactImportViewModel(DeviceContactRepository { rows }, ImportFakeRepository(), dispatcher)
        vm.permissionChanged(ContactPermissionState.GRANTED)
        advanceUntilIdle()
        assertEquals(3000, vm.uiState.value.selectedKeys.size)
        vm.updateQuery("测试2999")
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.visibleContacts.size)
        vm.selectAll(false)
        assertTrue(vm.uiState.value.selectedKeys.isEmpty())
    }
}

private class ImportFakeRepository(private val contacts: List<Contact> = emptyList()) : ContactRepository {
    var selections = emptyList<ContactImportSelection>()
    override suspend fun importDeviceContacts(selections: List<ContactImportSelection>): ContactImportResult {
        this.selections = selections
        return createAll(selections.filter { it.selected }.map { Contact(name = it.contact.name, phone = it.contact.phone) })
    }
    var calls = 0
    var fail = false
    var result = ContactImportResult(3, 0)
    var received = emptyList<Contact>()
    override suspend fun createAll(contacts: List<Contact>): ContactImportResult {
        calls++
        if (fail) error("测试写入异常")
        received = contacts
        return result
    }
    override fun observeContacts(query: String) = flowOf(contacts)
    override fun observeContactSummaries(query: String) = flowOf(emptyList<ContactLedgerSummary>())
    override fun observeContact(contactId: Long) = flowOf(contacts.find { it.id == contactId })
    override suspend fun create(contact: Contact) = 0L
    override suspend fun update(contact: Contact) = Unit
    override suspend fun delete(contact: Contact) = Unit
}
