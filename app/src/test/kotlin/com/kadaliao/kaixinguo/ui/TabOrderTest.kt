package com.kadaliao.kaixinguo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TabOrderTest {
    @Test
    fun searchPrecedesMineAndMineIsTheLastTab() {
        assertEquals(
            listOf("推荐", "排行榜", "新剧", "搜索", "我的"),
            Tab.entries.map { it.label },
        )
    }
}
