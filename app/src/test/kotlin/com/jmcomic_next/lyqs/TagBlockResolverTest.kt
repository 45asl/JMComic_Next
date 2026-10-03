package com.jmcomic_next.lyqs

import com.jmcomic_next.lyqs.data.BlockRules
import com.jmcomic_next.lyqs.data.TagBlockResolver
import com.jmcomic_next.lyqs.data.TagCache
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 列表标签屏蔽（1.5.1）。
 *
 * 这个功能的判定输入是**异步**的（标签只能从详情接口拿），所以出问题的方式不是「算错」，
 * 而是**代价失控**：重复请求、并发打满、失败就当命中、规则改了不生效。
 * 下面每一条都对着其中一种失败方式。
 */
class TagBlockResolverTest {

    /** 记录被请求过的 id；[failFor] 里的 id 返回 null（模拟网络失败）。 */
    private class FakeFetch(
        private val tags: Map<String, Set<String>>,
        private val failFor: Set<String> = emptySet(),
    ) {
        val calls = mutableListOf<String>()
        fun asFetch(): suspend (String) -> Set<String>? = { id ->
            calls += id
            if (id in failFor) null else tags[id] ?: emptySet()
        }
    }

    private fun scope() = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun `no tag rules means zero requests`() {
        val fetch = FakeFetch(mapOf("1" to setOf("巨乳")))
        val resolver = TagBlockResolver(fetch.asFetch(), scope())
        // 只给了关键词规则，没有标签规则
        resolver.setRules(BlockRules(words = setOf("广告")))
        resolver.request("1")
        resolver.request("2")
        assertEquals("没有标签规则就不该发任何请求", emptyList<String>(), fetch.calls)
        assertTrue(resolver.hidden.value.isEmpty())
    }

    @Test
    fun `hit is hidden and miss is not`() {
        val fetch = FakeFetch(
            mapOf("1" to setOf("巨乳", "單行本"), "2" to setOf("純愛")),
        )
        val resolver = TagBlockResolver(fetch.asFetch(), scope())
        resolver.setRules(BlockRules(tags = setOf("巨乳")))
        resolver.request("1")
        resolver.request("2")
        assertEquals(setOf("1"), resolver.hidden.value)
    }

    @Test
    fun `tag matching ignores case`() {
        val fetch = FakeFetch(mapOf("1" to setOf("Yaoi")))
        val resolver = TagBlockResolver(fetch.asFetch(), scope())
        resolver.setRules(BlockRules(tags = setOf("yaoi")))
        resolver.request("1")
        assertEquals(setOf("1"), resolver.hidden.value)
    }

    @Test
    fun `a failed fetch never hides`() {
        // 宁可漏杀不可错杀：取不到标签时不能当作命中
        val fetch = FakeFetch(mapOf("1" to setOf("巨乳")), failFor = setOf("1"))
        val resolver = TagBlockResolver(fetch.asFetch(), scope())
        resolver.setRules(BlockRules(tags = setOf("巨乳")))
        resolver.request("1")
        assertTrue("取失败必须保持显示", resolver.hidden.value.isEmpty())
    }

    @Test
    fun `a failed id is not retried endlessly`() {
        val fetch = FakeFetch(emptyMap(), failFor = setOf("1"))
        val resolver = TagBlockResolver(fetch.asFetch(), scope())
        resolver.setRules(BlockRules(tags = setOf("巨乳")))
        repeat(5) { resolver.request("1") }
        assertEquals("同一轮里失败的条目只请求一次", 1, fetch.calls.count { it == "1" })
    }

    @Test
    fun `the same id is requested only once`() {
        val fetch = FakeFetch(mapOf("1" to setOf("純愛")))
        val resolver = TagBlockResolver(fetch.asFetch(), scope())
        resolver.setRules(BlockRules(tags = setOf("巨乳")))
        repeat(4) { resolver.request("1") }
        assertEquals("缓存 + in-flight 去重后只应请求一次", listOf("1"), fetch.calls)
    }

    @Test
    fun `changing rules recomputes from cache without new requests`() {
        val fetch = FakeFetch(mapOf("1" to setOf("巨乳"), "2" to setOf("純愛")))
        val resolver = TagBlockResolver(fetch.asFetch(), scope())
        resolver.setRules(BlockRules(tags = setOf("巨乳")))
        resolver.request("1")
        resolver.request("2")
        assertEquals(setOf("1"), resolver.hidden.value)
        val callsBefore = fetch.calls.size

        // 加上「純愛」：应当立刻生效，且**不产生新请求**（标签不随规则变）
        resolver.setRules(BlockRules(tags = setOf("巨乳", "純愛")))
        assertEquals(setOf("1", "2"), resolver.hidden.value)
        assertEquals("规则变更不该重新请求", callsBefore, fetch.calls.size)

        // 清空规则：全部恢复显示
        resolver.setRules(BlockRules())
        assertTrue(resolver.hidden.value.isEmpty())
    }

    @Test
    fun `concurrency stays within the limit`() = runBlocking {
        val current = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val scope = CoroutineScope(Dispatchers.Default)
        val resolver = TagBlockResolver(
            fetchTags = { id ->
                val now = current.incrementAndGet()
                peak.updateAndGet { maxOf(it, now) }
                delay(40)
                current.decrementAndGet()
                setOf("tag-$id")
            },
            scope = scope,
            maxParallel = 3,
        )
        resolver.setRules(BlockRules(tags = setOf("不存在的标签")))
        repeat(12) { resolver.request("id$it") }
        delay(500)
        assertTrue("并发峰值 $peak 不该超过 3", peak.get() <= 3)
        assertTrue("确实跑起来了", peak.get() >= 1)
    }

    @Test
    fun `concurrent requests for the same id still fetch only once`() = runBlocking {
        // 这条钉的是 check-then-act 竞态：如果去重写成「先查再放」，多线程下同一部作品
        // 会被取两次（正是"同一 id 只请求一次"这条约束要防的事）。
        val calls = java.util.concurrent.atomic.AtomicInteger(0)
        val scope = CoroutineScope(Dispatchers.Default)
        val resolver = TagBlockResolver(
            fetchTags = { calls.incrementAndGet(); delay(20); setOf("純愛") },
            scope = scope,
            maxParallel = 8,
        )
        resolver.setRules(BlockRules(tags = setOf("巨乳")))
        // 用屏障把 20 个线程顶到同一瞬间再放行 —— 否则线程是错开的，
        // 竞态窗口小到测不出来（上一版就是这样，它其实抓不住旧 bug）。
        val barrier = java.util.concurrent.CyclicBarrier(20)
        val threads = (1..20).map { Thread { barrier.await(); resolver.request("same-id") } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        delay(300)
        assertEquals("20 个线程并发请求同一 id，只能取一次", 1, calls.get())
    }

    @Test
    fun `cache round trips through its text form`() {
        val cache = TagCache()
        cache.put("1", setOf("巨乳", "單行本"))
        cache.put("2", setOf("純愛"))
        val restored = TagCache().apply { load(cache.dump()) }
        assertEquals(setOf("巨乳", "單行本"), restored.get("1"))
        assertEquals(setOf("純愛"), restored.get("2"))
        assertEquals(null, restored.get("3"))
    }

    @Test
    fun `cache survives tags containing separators and control characters`() {
        // 回归测试：先前用 \u0001 当分隔符，而 XML 1.0 不能表示控制字符 ——
        // 写进 SharedPreferences 时被吞掉，读回来每部作品的标签会粘成一个巨型标签，
        // 于是**重启后屏蔽静默失效**。改用 JSON 之后必须无损。
        val nasty = setOf("巨乳", "NTR, 出軌", "带\"引号\"", "含\u0001控制符", "a\tb")
        val cache = TagCache()
        cache.put("1", nasty)
        val restored = TagCache().apply { load(cache.dump()) }
        assertEquals(nasty, restored.get("1"))
        // 落盘内容里不该出现裸控制字符（那正是被 XML 吞掉的那类）
        assertFalse(cache.dump().contains('\u0001'))
    }

    @Test
    fun `cache evicts the oldest beyond capacity`() {
        val cache = TagCache(capacity = 3)
        cache.put("1", setOf("a"))
        cache.put("2", setOf("b"))
        cache.put("3", setOf("c"))
        cache.put("4", setOf("d"))
        assertEquals(3, cache.size())
        assertEquals("最久未用到的应先被淘汰", null, cache.get("1"))
        assertEquals(setOf("d"), cache.get("4"))

        // 命中过的条目会被移到末尾，于是被淘汰的是下一个最旧的
        cache.get("2")
        cache.put("5", setOf("e"))
        assertEquals("刚用过的 2 不该被淘汰", setOf("b"), cache.get("2"))

        // 空字符串/空集合也要能往返
        assertFalse(cache.all().isEmpty())
        val empty = TagCache().apply { load("") }
        assertEquals(0, empty.size())
    }
}
