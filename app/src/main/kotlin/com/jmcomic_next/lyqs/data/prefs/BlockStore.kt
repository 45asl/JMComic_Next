package com.jmcomic_next.lyqs.data.prefs

import android.content.Context
import com.jmcomic_next.lyqs.data.BlockRules
import com.jmcomic_next.lyqs.data.remote.JmJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * 屏蔽名单的持久化。
 *
 * 用 SharedPreferences 存三份字符串数组（JSON），与 [AppPrefs] / [ReadProgressStore] 同一套路：
 * 条目数量是人手加的（几十条量级），不需要数据库，也就不必引入 Room/SQLite
 * —— 参考项目用了两个 SQLite 表，那是它自己的架构选择。
 *
 * 规则同时以 [state] 暴露成 Flow：列表页在规则变化后要重新过滤，
 * 而「我的 → 屏蔽设置」的增删都发生在别的页面上。
 */
class BlockStore(context: Context) : BlockStoreApi {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    override val state: StateFlow<BlockRules> = _state.asStateFlow()

    /** 当前规则的快照。列表过滤在数据层调用，走这里拿。 */
    override fun snapshot(): BlockRules = _state.value

    override fun addWord(word: String) = update { it.copy(words = it.words + word) }
    override fun removeWord(word: String) = update { it.copy(words = it.words - word) }
    override fun addTag(tag: String) = update { it.copy(tags = it.tags + tag) }
    override fun removeTag(tag: String) = update { it.copy(tags = it.tags - tag) }
    override fun addCategory(name: String) = update { it.copy(categories = it.categories + name) }
    override fun removeCategory(name: String) = update { it.copy(categories = it.categories - name) }

    /** 该条目是否已在名单里（界面用来提示「已屏蔽」而不是重复添加）。 */
    fun isBlocked(type: Kind, value: String): Boolean {
        val rules = _state.value
        return when (type) {
            Kind.Word -> rules.words.any { it.equals(value, ignoreCase = true) }
            Kind.Tag -> rules.tags.any { it.equals(value, ignoreCase = true) }
            Kind.Category -> rules.categories.any { it.equals(value, ignoreCase = true) }
        }
    }

    enum class Kind { Word, Tag, Category }

    private fun update(transform: (BlockRules) -> BlockRules) {
        val next = transform(_state.value)
        write(next)
        _state.value = next
    }

    private fun read(): BlockRules {
        fun load(key: String): Set<String> =
            prefs.getString(key, null)
                ?.let { runCatching { JmJson.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull() }
                ?.toSet()
                .orEmpty()

        return BlockRules(
            words = load(KEY_WORDS),
            tags = load(KEY_TAGS),
            categories = load(KEY_CATEGORIES),
        )
    }

    private fun write(rules: BlockRules) {
        prefs.edit()
            .putString(KEY_WORDS, JmJson.encodeToString(ListSerializer(String.serializer()), rules.words.toList()))
            .putString(KEY_TAGS, JmJson.encodeToString(ListSerializer(String.serializer()), rules.tags.toList()))
            .putString(
                KEY_CATEGORIES,
                JmJson.encodeToString(ListSerializer(String.serializer()), rules.categories.toList()),
            )
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "jm_block"
        const val KEY_WORDS = "words"
        const val KEY_TAGS = "tags"
        const val KEY_CATEGORIES = "categories"
    }
}
