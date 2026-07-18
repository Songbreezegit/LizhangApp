package com.yangsong.lizhang.domain.model

/** 联系人是礼金往来的主体，不与账号体系绑定。 */
data class Contact(
    val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val relationship: String? = null,
    val notes: String? = null,
    val createdTime: Long = System.currentTimeMillis(),
)
