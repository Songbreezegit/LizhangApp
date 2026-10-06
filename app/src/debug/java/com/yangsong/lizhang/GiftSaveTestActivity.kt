package com.yangsong.lizhang

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

/** 礼金保存测试在窗口首次附着前采用与真实页面相同的边到边配置。 */
class GiftSaveTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }
}
