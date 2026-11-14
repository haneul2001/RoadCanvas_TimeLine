package com.carnect.roadcanvas.deco

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 지도 꾸미기 상태. 바뀔 때마다 기기 안에 저장한다. */
class DecorRepo(context: Context) {
    private val store = DecorStore(context)

    private val _items = MutableStateFlow(store.load())
    val items: StateFlow<List<Decoration>> = _items.asStateFlow()

    private val _custom = MutableStateFlow(store.loadCustom())
    val custom: StateFlow<List<CustomSticker>> = _custom.asStateFlow()

    /** 되돌리기용: 지운 것도 되살릴 수 있게 바뀌기 전 목록을 쌓아 둔다. */
    private val history = ArrayDeque<List<Decoration>>()

    private fun set(list: List<Decoration>) {
        history.addLast(_items.value)
        if (history.size > 50) history.removeFirst()
        _items.value = list
        store.save(list)
    }

    fun add(d: Decoration) = set(_items.value + d)

    fun remove(ids: Set<Long>) {
        if (ids.isEmpty()) return
        set(_items.value.filterNot { it.id in ids })
    }

    fun clear() {
        if (_items.value.isNotEmpty()) set(emptyList())
    }

    val canUndo: Boolean get() = history.isNotEmpty()

    fun undo() {
        val prev = history.removeLastOrNull() ?: return
        _items.value = prev
        store.save(prev)
    }

    fun addCustom(emoji: String, text: String, color: Int): CustomSticker {
        val s = CustomSticker(System.currentTimeMillis(), emoji, text.take(MAX_TEXT), color)
        _custom.value = _custom.value + s
        store.saveCustom(_custom.value)
        return s
    }

    fun removeCustom(id: Long) {
        _custom.value = _custom.value.filterNot { it.id == id }
        store.saveCustom(_custom.value)
    }

    /** 여행 기록 전체 삭제와 함께 꾸미기도 지운다. */
    fun wipe() {
        store.clear()
        history.clear()
        _items.value = emptyList()
        _custom.value = emptyList()
    }

    companion object {
        const val MAX_TEXT = 10
    }
}
