package com.yangsong.lizhang.ui.navigation

import com.yangsong.lizhang.core.common.NavigationConstants

sealed class AppDestination(val route: String) {
    data object Home : AppDestination("home")
    data object AddGift : AppDestination("add_gift")
    data object AddGiftForContact : AppDestination(
        "add_gift/contact/{${NavigationConstants.CONTACT_ID_ARGUMENT}}",
    ) {
        fun createRoute(contactId: Long) = "add_gift/contact/$contactId"
    }
    data object GiftRecordDetail : AppDestination("gift_record/{${NavigationConstants.RECORD_ID_ARGUMENT}}") {
        fun createRoute(recordId: Long) = "gift_record/$recordId"
    }
    data object GiftRecordEditor : AppDestination("gift_record_editor/{${NavigationConstants.RECORD_ID_ARGUMENT}}") {
        fun createRoute(recordId: Long) = "gift_record_editor/$recordId"
    }
    data object Contacts : AppDestination("contacts")
    data object ContactImport : AppDestination("contact_import")
    data object ContactDetail : AppDestination("contact/{${NavigationConstants.CONTACT_ID_ARGUMENT}}") {
        fun createRoute(contactId: Long) = "contact/$contactId"
    }
    data object ContactEditor : AppDestination("contact_editor/{${NavigationConstants.CONTACT_ID_ARGUMENT}}") {
        fun createRoute(contactId: Long = NavigationConstants.NEW_CONTACT_ID) = "contact_editor/$contactId"
    }
    data object Statistics : AppDestination("statistics")
    data object Search : AppDestination("search")
    data object Settings : AppDestination("settings")
    data object FontGuide : AppDestination("font_guide")
    data object About : AppDestination("about")
    data object Privacy : AppDestination("privacy")
    data object ReceivedRecords : AppDestination("records_received")
    data object GivenRecords : AppDestination("records_given")
    data object Calendar : AppDestination("calendar")
    data object Notifications : AppDestination("notifications")
}
