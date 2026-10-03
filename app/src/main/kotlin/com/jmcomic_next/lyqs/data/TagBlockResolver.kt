package com.jmcomic_next.lyqs.data

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 列表里的**标签屏蔽**（1.5.1）。
 *
 * 判定所需的输入是**异步**的：列表接口只给 `name` / `author` / `category`，**不给标签**；
 * 标签只存在于详情接口（`AlbumDetail.tags`）。所以这里做的是「二次后台读取」：
 * 条目在列表里可见时请求一次 → 后台取详情拿标签 → 落缓存 → 命中标签规则就报出这个 id，
 * 由列表把它滤掉。列表层不需要知道这些细节，只读 [hidden]。
 *
 * 六条自我约束（都是硬要求，逐条对应到实现）：
 *  1. **没有标签规则就什么都不做** —— [request] 首行即 return，零请求零开销；
 *  2. **并发有界** —— [maxParallel] 用 `Semaphore` 限流，不会把 N 个请求同时打出去；
 *  3. **同一 id 只请求一次** —— 缓存命中直接返回，未完成的记在 [inFlight] 去重；
 *  4. **失败不隐藏** —— [fetchTags] 返回 null 视为失败：不写缓存、不判命中（宁可漏杀不可错杀）；
 *  5. **规则变更立刻重算** —— [setRules] 只对**已缓存**的标签重算，不重新发请求
 *     （标签本身不随规则变，重新请求纯属浪费）；
 *  6. **不造成列表跳动** —— 由列表层给条目加 `Modifier.animateItem()`，见调用处注释。
 *
 * 另外 [failed] 记住本会话里取失败过的 id，避免同一轮里反复重试同一个坏条目；
 * 规则变化时会清空它，给这些条目一次重试机会。
 */
class TagBlockResolver(
    private val fetchTags: suspend (String) -> Set<String>?,
    private val scope: CoroutineScope,
    private val cache: TagCache = TagCache(),
    private val maxParallel: Int = 3,
    /** 缓存变化后的落盘回调；传空实现即可（单测用内存缓存）。 */
    private val onPersist: (String) -> Unit = {},
) {
    private val _hidden = MutableStateFlow<Set<String>>(emptySet())
    /** 命中标签规则、应当从列表里隐藏的作品 id。 */
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()

    private val semaphore = Semaphore(maxParallel.coerceAtLeast(1))

    /**
     * 这两个集合会被**多个 IO 协程和主线程同时读写**（[request] 在组合期间从主线程调用，
     * 协程里做 `remove`），所以必须是并发集合 —— 普通 `mutableSetOf` 在这里会被写坏。
     * 这一点和 [TagCache] 是同一类错误，只是那次是在缓存上犯的。
     */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val failed = ConcurrentHashMap.newKeySet<String>()

    /** 会被协程读取、被主线程的规则收集器写入，所以要有可见性保证。 */
    @Volatile
    private var rules: BlockRules = BlockRules()

    /** 规则变了：立刻按缓存重算，并给此前失败的条目一次重试机会。 */
    fun setRules(newRules: BlockRules) {
        rules = newRules
        failed.clear()
        recompute()
    }

    /** 列表条目可见时调用。没有标签规则、已缓存、正在请求、或已知失败 → 直接返回。 */
    fun request(id: String) {
        if (rules.tags.isEmpty()) return                       // 约束 1
        if (cache.get(id) != null) return                      // 约束 3（已缓存）
        if (id in failed) return                               // 约束 3（已知失败）
        // 用 add 的返回值做去重，而不是「先查再放」—— 后者在多线程下是 check-then-act 竞态，
        // 同一部作品会被两个协程各取一次（正是这条约束要防的事）。
        // 用 add 的返回值做去重，而不是「先查再放」—— 后者在多线程下是 check-then-act 竞态，
        // 同一部作品会被两个协程各取一次（测试用屏障顶住 20 个线程能稳定复现）。
        if (!inFlight.add(id)) return
        scope.launch {
            val tags = semaphore.withPermit {                        // 约束 2
                runCatching { fetchTags(id) }.getOrNull()
            }
            inFlight.remove(id)
            if (tags == null) {                                  // 约束 4
                failed.add(id)
                return@launch
            }
            cache.put(id, tags)
            onPersist(cache.dump())
            recompute()
        }
    }

    /** 只用**已缓存**的标签重算命中集合（约束 5：不发任何请求）。 */
    private fun recompute() {
        if (rules.tags.isEmpty()) {                              // 约束 1
            _hidden.value = emptySet()
            return
        }
        _hidden.value = cache.all()
            .filterValues { rules.matchesTags(it) }
            .keys
    }

    /** 供启动时把落盘内容读回缓存；读完请再调一次 [setRules] 或 [restore]。 */
    fun restore(persisted: String?) {
        cache.load(persisted)
    }

    /** 当前缓存大小的只读视图，供测试与设置界面展示。 */
    fun cachedCount(): Int = cache.size()
}
