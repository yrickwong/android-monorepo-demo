package com.demo.foundations.assemblekit.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.demo.foundations.assemblekit.PageContext
import com.demo.foundations.assemblekit.ViewPage
import com.demo.foundations.assemblekit.setPageContext
import com.demo.thirdparty.logger.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * [ListPage] 的异构版本：[itemsFlow] 里的每个 item 可以是公共父类型 [T] 的不同子类型，
 * 由不同的 [ItemBinder] 渲染——具体用哪个 binder，是在运行时基于 class 注册表查出来的。
 *
 * ## 为什么要单独搞一个 Page（而不是 "在 ListPage 上加个 flag"）
 *
 * 单类型的 [ListPage] 覆盖了 95% 的场景。强迫每个列表都去走一份 per-item 分发表会：
 * (a) 让简单调用点也得写一个只有一条记录的 builder，
 * (b) 让所有 binder 在 hot bind path 上都得过一次没必要的 `Class.isInstance` 检查，
 * (c) 让 "这个列表里所有 row 都等价" 这种代码语义变模糊。两个 Page 除了 RecyclerView
 * 本身强加的接口之外没有任何公共 API；保持它们分开，比把其中一个参数化成能同时干两件事，
 * 代价更低。
 *
 * 已有的 [ItemBinder] 实现可以直接用——不用再实现任何新接口。
 * [MultiTypeListPage] 是严格加法式的新 Page 类型；[ListPage] 完全没动。
 *
 * ## 用法
 *
 * ```kotlin
 * sealed interface FeedRow {
 *     data class NoteRow(val note: Note) : FeedRow
 *     data class AdRow(val ad: Ad)       : FeedRow
 *     data object LoadingRow             : FeedRow
 * }
 *
 * class NoteRowBinder    : ItemBinder<FeedRow.NoteRow>    { ... }
 * class AdRowBinder      : ItemBinder<FeedRow.AdRow>      { ... }
 * class LoadingRowBinder : ItemBinder<FeedRow.LoadingRow> { ... }
 *
 * // 在 assemble { } 里：
 * +MultiTypeListPage(
 *     itemsFlow = vm.stateFlow.map { it.feedRows }.distinctUntilChanged(),
 * ) {
 *     bind<FeedRow.NoteRow>(NoteRowBinder())
 *     bind<FeedRow.AdRow>(AdRowBinder())
 *     bind<FeedRow.LoadingRow>(LoadingRowBinder())
 * } at R.id.feed_body_slot
 * ```
 *
 * ## 分发规则
 *
 *  - item → binder 的查找是在 `bind<T>()` 声明的类型上做 `Class.isInstance`，
 *    **按注册顺序**遍历。如果你的 `sealed` 层级里一个 row 有可能匹配多个 entry，
 *    把更具体的类型注册在前面。
 *  - 如果一个 item 的 class 谁都不匹配，那是程序员错误：下次 bind 时会以
 *    [IllegalStateException] 抛出，错误信息里带上肇事 class 名。请改成显式的
 *    "loading" / "error" row 类型，不要靠 `null` 或者 "其它一切" 的语义。
 *  - DiffUtil 会把不同类型的 row 视为不同 item，即使它们 `==` 起来相等；
 *    这能避免框架试图把 `AdRow` 的 view 当成 `NoteRow` 重绑。
 *
 * ## 从 [ListPage] 继承下来的部分
 *
 *  - 每个 row 的 `itemView` 在 `onCreateViewHolder` 里都会被打上父 Page 的
 *    [PageContext] tag，深层子 View 因此可以通过
 *    [com.demo.foundations.assemblekit.findPageContext] 解析到。
 *  - `itemsFlow` 通过 [pageScope] 上的 `collectLatest` 来收集；
 *    detach / [com.demo.foundations.assemblekit.Assembly.replace] 会取消该 collector
 *    并清掉 adapter 引用。
 *  - `itemsFlow` 应该是从 Shell VM 的 `stateFlow.map { ... }.distinctUntilChanged()`
 *    派生出来的；不要直接喂一个 hot 的 repository flow——和 [ListPage] 一样的 MVI 规则。
 */
open class MultiTypeListPage<T : Any>(
    private val itemsFlow: Flow<List<T>>,
    binders: MultiTypeBindersBuilder<T>.() -> Unit,
    private val layoutManagerFactory: (parent: ViewGroup) -> RecyclerView.LayoutManager = { parent ->
        LinearLayoutManager(parent.context)
    },
    explicitId: String? = null,
) : ViewPage(explicitId = explicitId) {

    private val entries: List<TypedBinderEntry<T, out T>> =
        MultiTypeBindersBuilder<T>().apply(binders).entries.toList().also {
            require(it.isNotEmpty()) {
                "MultiTypeListPage requires at least one bind<T>() entry"
            }
        }

    private var recyclerView: RecyclerView? = null
    private var adapter: MultiBinderAdapter<T>? = null

    final override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup): View {
        // 与 ListPage 同样的注意点：显式把父 Page 的 PageContext 捕获下来。
        // 在 `apply` 块内部，`context` 会解析成 View.getContext()（一个 Android Context）。
        val pageCtx: PageContext = context
        val rv = RecyclerView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            layoutManager = layoutManagerFactory(parent)
            adapter = MultiBinderAdapter(entries, pageCtx).also { this@MultiTypeListPage.adapter = it }
        }
        recyclerView = rv
        return rv
    }

    final override fun onViewCreated(view: View) {
        val adapter = adapter ?: return
        pageScope.launch {
            itemsFlow.collectLatest { items ->
                try {
                    adapter.submitList(items)
                } catch (t: Throwable) {
                    Logger.w(LOG_TAG, "submitList failed: ${t.message}")
                }
            }
        }
    }

    final override fun onDestroyView() {
        recyclerView?.adapter = null
        recyclerView = null
        adapter = null
    }

    // ---- adapter / diff -----------------------------------------------

    private class MultiBinderAdapter<T : Any>(
        private val entries: List<TypedBinderEntry<T, out T>>,
        private val parentCtx: PageContext,
    ) : ListAdapter<T, BinderViewHolder>(MultiDiffCallback(entries)) {

        override fun getItemViewType(position: Int): Int {
            val item = getItem(position)
            return indexOfBinderFor(item, entries)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BinderViewHolder {
            @Suppress("UNCHECKED_CAST")
            val binder = entries[viewType].binder as ItemBinder<Any?>
            val rowView = binder.createView(parent, parentCtx)
            // 给 row 根节点打 tag，让子孙 View 能通过 view.findPageContext()
            // 拿到本 Page 的 PageContext——逻辑与 ListPage 一致。
            rowView.setPageContext(parentCtx)
            return BinderViewHolder(rowView)
        }

        override fun onBindViewHolder(holder: BinderViewHolder, position: Int) {
            val item = getItem(position)
            val idx = indexOfBinderFor(item, entries)
            @Suppress("UNCHECKED_CAST")
            val binder = entries[idx].binder as ItemBinder<Any?>
            binder.bind(holder.itemView, item, position, parentCtx)
        }

        override fun onViewRecycled(holder: BinderViewHolder) {
            // 我们没法在不从 position 反推的前提下知道某个被回收的 holder 属于
            // 哪个 binder，而 RecyclerView 在 recycle 时机并不提供 position。
            // 这里直接 no-op；如果 binder 真的需要显式 unbind 钩子，可以子类化
            // 并覆写 onBindViewHolder，再用一个以 holder 身份为 key 的旁挂 map 维护。
            // 对常见场景（text / image / 点击监听）来说，不需要做任何动作。
        }
    }

    private class BinderViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private class MultiDiffCallback<T : Any>(
        private val entries: List<TypedBinderEntry<T, out T>>,
    ) : DiffUtil.ItemCallback<T>() {

        override fun areItemsTheSame(old: T, new: T): Boolean {
            val oldIdx = indexOfBinderForOrNull(old, entries)
            val newIdx = indexOfBinderForOrNull(new, entries)
            // 不同 row 类型永远不算 "同一个 item"——避免 RecyclerView 试图把一个
            // NoteRow 的 holder 当成 AdRow 来重绑。
            if (oldIdx == null || newIdx == null || oldIdx != newIdx) return false
            @Suppress("UNCHECKED_CAST")
            val binder = entries[oldIdx].binder as ItemBinder<Any?>
            return binder.areItemsTheSame(old, new)
        }

        override fun areContentsTheSame(old: T, new: T): Boolean {
            val idx = indexOfBinderForOrNull(old, entries) ?: return false
            if (indexOfBinderForOrNull(new, entries) != idx) return false
            @Suppress("UNCHECKED_CAST")
            val binder = entries[idx].binder as ItemBinder<Any?>
            return binder.areContentsTheSame(old, new)
        }
    }

    companion object {
        private const val LOG_TAG = "MultiTypeListPage"

        private fun <T : Any> indexOfBinderFor(
            item: T,
            entries: List<TypedBinderEntry<T, out T>>,
        ): Int {
            val idx = indexOfBinderForOrNull(item, entries)
            checkNotNull(idx) {
                "MultiTypeListPage: no ItemBinder registered for item of type " +
                    "${item.javaClass.name}. Registered: ${entries.joinToString { it.type.name }}"
            }
            return idx
        }

        private fun <T : Any> indexOfBinderForOrNull(
            item: T,
            entries: List<TypedBinderEntry<T, out T>>,
        ): Int? {
            for (i in entries.indices) {
                if (entries[i].type.isInstance(item)) return i
            }
            return null
        }
    }
}

/**
 * 由 [MultiTypeListPage] 构造函数收集的 DSL builder。用 [bind] 给列表元素的公共类型
 * 下的每个具体子类型注册一个 [ItemBinder]。
 *
 * 注册顺序在一个 row 可能匹配多个 entry 时是有意义的——靠前的 `bind<T>()` 胜出。
 * 把最具体的类型注册在最前面。
 */
class MultiTypeBindersBuilder<T : Any> @PublishedApi internal constructor() {

    @PublishedApi
    internal val entries: MutableList<TypedBinderEntry<T, out T>> = mutableListOf()

    /**
     * 把 [binder] 注册为所有运行时 class 可赋值给 [C] 的 item 的渲染器。
     */
    inline fun <reified C : T> bind(binder: ItemBinder<C>) {
        entries += TypedBinderEntry(C::class.java, binder)
    }
}

/**
 * "具体 row 类型" + "知道怎么渲染它的 binder" 的内部配对。设为 public 仅仅是因为
 * [MultiTypeBindersBuilder] 上 inline 的 `bind<C>()` 扩展需要在产品代码里构造它。
 */
class TypedBinderEntry<T : Any, C : T> @PublishedApi internal constructor(
    internal val type: Class<C>,
    internal val binder: ItemBinder<C>,
)
