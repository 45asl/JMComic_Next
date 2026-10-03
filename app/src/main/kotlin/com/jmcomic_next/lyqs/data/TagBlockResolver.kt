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

    /**
     * 命中原因：作品 id → **命中的标签规则**（1.5.2）。
     *
     * 只报一个 id 集合时，界面只能说「隐藏了 N 条」；搜索页要告诉用户
     * 「是哪些标签挡的」并给出「允许一次」，所以这里把原因一起报出来。
     * 值与 [hidden] 恒一致：`blockedBy.keys == hidden`。
     *
     * 返回的是**规则里的写法**（见 [BlockRules.hitRuleTags]），不是作品上的写法。
     */
    private val _blockedBy = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val blockedBy: StateFlow<Map<String, Set<String>>> = _blockedBy.asStateFlow()

    private val semaphore = Semaphore(maxParallel.coerceAtLeast(1))

    /**
     * 这两个集合会被**多个 IO 协程和主线程同时读写**（[request] 在组合期间从主线程调用，
     * 协程里做 `remove`），所以必须是并发集合 —— 普通 `mutableSetOf` 在这里会被写坏。
     * 这一点和 [TagCache] 是同一类错误，只是那次是在缓存上犯的。
     */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val failed = ConcurrentHashMap.newKeySet<String>()

    /**
     * 「允许一次」放行过的 id（1.5.2）。
     *
     * 只活在内存里，且**作用域是当前规则 + 当前这次搜索**：
     *  - [setRules] 收到**不同**的规则时清空 —— 用户改了屏蔽名单，就该按新规则重新判一遍；
     *  - [clearAllowances] 由搜索页在**发起新一次搜索**时调用 —— 上一次的放行不该跟到下一次；
     *  - 进程被杀即失效（本来就是「一次」）。
     *
     * 它与 [rules] 是两回事：这里只记「这一次先让他看」，**绝不写回 [BlockRules]**，
     * 用户的持久屏蔽名单从头到尾不被改动。
     */
    private val allowedOnce = ConcurrentHashMap.newKeySet<String>()

    /** 会被协程读取、被主线程的规则收集器写入，所以要有可见性保证。 */
    @Volatile
    private var rules: BlockRules = BlockRules()

    /** 规则变了：立刻按缓存重算，并给此前失败的条目一次重试机会。 */
    @Synchronized
    fun setRules(newRules: BlockRules) {
        val changed = newRules != rules
        rules = newRules
        failed.clear()
        // 规则真的变了 → 此前「允许一次」的判定依据已经不存在，作废重判。
        // （只在真的不同时清：BlockStore 每次发射都会调到这里，包括改关键词/分类，
        //  清空的动作本身无害，但没必要为一个等值快照制造一次全量重算。）
        if (changed) allowedOnce.clear()
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

    /**
     * 读**已缓存**的标签；没有则返回 null（1.6.0）。
     *
     * 开放出来是为了让别的页面复用这份缓存。之前随机页与收藏扫描各自调 `repo.album(id)` 取标签，
     * 于是首页/搜索/分类**已经读过**的作品，它们会再读一遍 —— 同一份数据、同一个进程、两次请求。
     */
    @Synchronized
    fun cachedTags(id: String): Set<String>? = cache.get(id)

    /**
     * 把别处读到的标签回填进缓存（1.6.0）。
     *
     * 回填之后：同一部作品在本次运行内不会再被读第二次（缓存 + 落盘），
     * 而且如果它命中屏蔽规则，列表会通过 [hidden] 收敛 —— 与解析器自己读到时的行为一致。
     */
    @Synchronized
    fun rememberTags(id: String, tags: Set<String>) {
        if (tags.isEmpty()) return
        cache.put(id, tags)
        onPersist(cache.dump())
        recompute()
    }

    /** 只用**已缓存**的标签重算命中集合（约束 5：不发任何请求）。 */
    @Synchronized
    private fun recompute() {
        if (rules.tags.isEmpty()) {                              // 约束 1
            _blockedBy.value = emptyMap()
            _hidden.value = emptySet()
            return
        }
        val reasons = LinkedHashMap<String, Set<String>>()
        cache.all().forEach { (id, tags) ->
            // 「允许一次」放行的条目直接跳过：**这一次**不再因为标签被隐藏。
            // 注意这里不能顺手把它从 cache 里删掉 —— 缓存是网络事实，
            // 放行是用户当下的一次选择，删缓存会让别的页面白白重取一遍。
            if (id in allowedOnce) return@forEach
            val hit = rules.hitRuleTags(tags)
            if (hit.isNotEmpty()) reasons[id] = hit.toSet()
        }
        // 两个 flow 一起换：blockedBy.keys == hidden 必须恒成立，
        // 否则界面会「列着标签却找不到对应作品」或反过来。方法已经 @Synchronized。
        _blockedBy.value = reasons
        _hidden.value = reasons.keys
    }

    /**
     * 「允许一次」：把 [ids] 从隐藏集合里放出来，并在**本次会话的这个规则/这次搜索内**
     * 不再因为标签把它们隐藏（1.5.2）。
     *
     * 语义（与 [clearAllowances] / [setRules] 一起构成完整作用域）：
     *  - **立即生效**：不等下一次 [recompute]，点击那一刻 [hidden] 与 [blockedBy] 就更新；
     *  - **不会被后续结果塞回去**：放行记录在 [allowedOnce] 里，之后同一轮解析里
     *    别的作品取回标签触发的 [recompute] 不会重新隐藏这些 id；
     *  - **失效时机**：① [setRules] 收到不同的规则；② 搜索页发起新一次搜索调用
     *    [clearAllowances]；③ 进程结束。三者任一发生后，它们重新按规则判定；
     *  - **不改规则**：不碰 [BlockRules]，也不写 [com.jmcomic_next.lyqs.data.prefs.BlockStore]，
     *    所以设置页里的屏蔽名单原样不变，重启后照旧屏蔽。
     */
    @Synchronized
    fun allowOnce(ids: Set<String>) {
        if (ids.isEmpty()) return
        allowedOnce.addAll(ids)
        val next = _blockedBy.value - ids
        _blockedBy.value = next
        _hidden.value = next.keys
    }

    /**
     * 撤销所有「允许一次」并立刻重算 —— 搜索页在**发起新一次搜索**时调用，
     * 于是上一次的放行只活在那一次搜索里，不会跟到下一次。
     * 没有放行记录时是空操作（不制造无谓的重算）。
     */
    @Synchronized
    fun clearAllowances() {
        if (allowedOnce.isEmpty()) return
        allowedOnce.clear()
        recompute()
    }

    /** 供启动时把落盘内容读回缓存；读完请再调一次 [setRules] 或 [restore]。 */
    fun restore(persisted: String?) {
        cache.load(persisted)
    }

    /** 当前缓存大小的只读视图，供测试与设置界面展示。 */
    fun cachedCount(): Int = cache.size()
}
