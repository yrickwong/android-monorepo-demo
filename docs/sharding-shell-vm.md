# Shell VM 拆分指南（Sharding the Shell ViewModel）

> 这是 [`mvi-rules.md`](mvi-rules.md) Rule M2「**每屏一个 Shell ViewModel**」的**进阶**伴生文档。
> M2 不是"每屏只能有一个类叫 `*ShellViewModel` 的 Mavericks VM"，而是"每屏的状态有**唯一真相源拓扑**"。
> 当单屏的功能堆叠到让一个 `XxxShellViewModel` 体积失控时，怎么"拆"才不破坏 M2，是这篇要回答的问题。

> **TL;DR**：先尽量不拆，用 **state 内部组合**（小数据类聚合）解决 80% 的"VM 太大"。真到了必须拆 VM 的程度，按"垂直切片"（一块业务一块 sub-VM）而不是"水平切片"（数据层一个、UI 层一个）拆，并且**所有跨切片协同必须收回 `XxxShellViewModel` 这一个对外门面**——子 Page / 深层 widget 看到的入口仍然只有一个 `XxxShellViewModelKey`。

---

## 1. 何时该想"拆"

下列任一信号出现，就值得评估拆分；**两个以上同时出现**，基本就该动手了：

| 信号 | 量化阈值（经验，非死规则） |
| --- | --- |
| `XxxShellState` 字段数 | > 15 个，或一屏出现 3+ 个互不相关的子领域（订单 / 评论 / 推荐 …） |
| `XxxShellViewModel` 方法数 | > 20 个公开命令，或方法名前缀已经自然分组 |
| `setState { copy(...) }` 的 `copy` 字段总数 | 单次 `copy` > 8 个字段，或一次更新需要"打散重组"两层嵌套 data class |
| `MavericksViewModelFactory.create` 里的构造参数 | > 4 个 Repository / Service |
| 同一个 VM 文件行数 | > 400 行（**警告线**），> 800 行（**必须拆**） |
| 一个改动 PR 同时改这个 VM 的 5+ 个不相关方法 | 说明它已经成了 sink，再加东西就越来越没人 review 得动 |

**反信号**——下面这些请*不要*拆：

- "我觉得 VM 太长了，但 state 字段就 6 个"——这是命名 / 抽辅助函数的工作，不是拆 VM 的工作。
- "我想给某个子模块单独写单测"——`MavericksViewModel` 自己就好测；提取一个普通 Kotlin 类（不是另一个 VM）做"逻辑核心" + VM 薄包装是更好的答案。
- "我想让某个 Page 拥有自己的局部状态"——那是 `by pageViewModel()` 的活，不是拆 Shell VM。见 [`PageViewModel`](../foundations/assemblekit/src/main/java/com/demo/foundations/assemblekit/PageViewModel.kt)。

---

## 2. 拆之前先用「state 内部组合」

最容易被忽略、收益最高的一步——把 `XxxShellState` 内部按业务**子领域**聚合成小 data class，VM 方法用扩展函数 / `copy(field = field.copy(...))` 分组管理。

**反例**（扁平、字段长尾）：

```kotlin
data class FeedShellState(
    val notes: Async<List<Note>> = Uninitialized,
    val showBanner: Boolean = false,
    val bannerText: String = "",
    val bannerClickedTimes: Int = 0,
    val composerOpen: Boolean = false,
    val composerDraft: String = "",
    val composerAttachments: List<Uri> = emptyList(),
    val composerSubmitting: Boolean = false,
    val filterAuthor: String? = null,
    val filterTags: Set<String> = emptySet(),
    val filterSort: SortMode = SortMode.NEWEST,
    // … 还有十几个 …
) : MavericksState
```

**改良**（子领域聚合）：

```kotlin
data class BannerSlice(
    val show: Boolean = false,
    val text: String = "",
    val clickedTimes: Int = 0,
)

data class ComposerSlice(
    val open: Boolean = false,
    val draft: String = "",
    val attachments: List<Uri> = emptyList(),
    val submitting: Boolean = false,
)

data class FilterSlice(
    val author: String? = null,
    val tags: Set<String> = emptySet(),
    val sort: SortMode = SortMode.NEWEST,
)

data class FeedShellState(
    val notes: Async<List<Note>> = Uninitialized,
    val banner: BannerSlice = BannerSlice(),
    val composer: ComposerSlice = ComposerSlice(),
    val filter: FilterSlice = FilterSlice(),
) : MavericksState
```

`viewModel.onEach(FeedShellState::banner)` 仍然按引用相等去重——只有 banner 子领域真变了，订阅者才被通知。等价于"分了 VM 但没真分"，零结构性代价。

### VM 方法用扩展函数分组

```kotlin
class FeedShellViewModel(...) : MavericksViewModel<FeedShellState>(...) {

    // —— Notes 领域 ——
    fun refresh() = suspend { repo.load() }.execute { copy(notes = it) }
    fun likeOne(id: String) = setState { copy(notes = Success(repo.like(id))) }

    // —— Banner 领域 ——（内部 helper 收编到 BannerSlice 上）
    fun showBanner(text: String) = setState { copy(banner = banner.copy(show = true, text = text)) }
    fun hideBanner()             = setState { copy(banner = banner.copy(show = false)) }
    fun onBannerClicked()        = setState { copy(banner = banner.copy(clickedTimes = banner.clickedTimes + 1)) }

    // —— Composer 领域 ——
    fun openComposer()           = setState { copy(composer = composer.copy(open = true)) }
    // ...
}
```

绝大多数"VM 太大"问题到这一步就解决了。**只有当一个领域复杂到自身就有 8+ 字段 / 8+ 方法**，再考虑下一节。

---

## 3. 真要拆 VM 的两种合法形态

### 形态 A：垂直切片（Feature sub-VM，**推荐默认**）

**何时**：一屏装了 2-4 个**业务正交**的功能区，每个区域自己就够格做一个独立 Mavericks VM。例如 Feed 同屏装了"内容列表 + 评论抽屉 + 直播预告条"。

**做法**：每个子领域一个 `*FeatureViewModel : MavericksViewModel<*FeatureState>`，外面套**一个**"Shell"角色的轻 VM，叫 `XxxShellViewModel`——它本身**几乎不持有状态**，只持有对各子 VM 的引用，并把"跨切片需要协调的事"做成自己的命令。

```kotlin
val FeedShellViewModelKey = pageContextKey<FeedShellViewModel>("feed.shell")

data class FeedShellState(
    val activeTab: FeedTab = FeedTab.FOR_YOU,   // 真正"全屏共享"的最小状态
) : MavericksState

class FeedShellViewModel(
    initialState: FeedShellState,
    val notes:    NotesFeatureViewModel,        // 子 VM：列表 + 拉取 + 点赞
    val comments: CommentsFeatureViewModel,     // 子 VM：评论抽屉
    val live:     LiveTeaserFeatureViewModel,   // 子 VM：直播预告条
) : MavericksViewModel<FeedShellState>(initialState) {

    /** 跨切片协同：切 tab 要同时停掉评论抽屉里的轮询。 */
    fun switchTab(tab: FeedTab) {
        setState { copy(activeTab = tab) }
        comments.closeIfOpen()
        live.pauseAutoRefresh(tab != FeedTab.FOR_YOU)
    }

    companion object : MavericksViewModelFactory<FeedShellViewModel, FeedShellState> {
        override fun create(vc: ViewModelContext, s: FeedShellState): FeedShellViewModel {
            // 子 VM 都活在同一 ActivityViewModelContext 下；keys 用稳定字符串
            val notes    = MavericksViewModelProvider.get(NotesFeatureViewModel::class.java,
                            NotesFeatureState::class.java, vc, "feed_notes")
            val comments = MavericksViewModelProvider.get(CommentsFeatureViewModel::class.java,
                            CommentsFeatureState::class.java, vc, "feed_comments")
            val live     = MavericksViewModelProvider.get(LiveTeaserFeatureViewModel::class.java,
                            LiveTeaserFeatureState::class.java, vc, "feed_live")
            return FeedShellViewModel(s, notes, comments, live)
        }
    }
}
```

Host 仍然只 `provides` 一个 Shell：

```kotlin
assemble {
    provides(FeedShellViewModelKey, shellVm)
    +FeedHeaderPage()     at R.id.feed_header_slot
    +FeedNotesListPage()  at R.id.feed_body_slot
    +FeedCommentsPage()   at R.id.feed_drawer_slot
    +FeedLiveTeaserPage() at R.id.feed_live_slot
    +FeedFooterPage()     at R.id.feed_footer_slot
}
```

子 Page **可以直接拿子 VM**，但必须**经过 Shell** 拿，不要单独 `provides` 子 VM：

```kotlin
class FeedNotesListPage(...) : ListPage<Note>(...) {
    override fun onViewCreated(view: View) {
        val shell = requireConsume(FeedShellViewModelKey)
        shell.notes.onEach(NotesFeatureState::items) { /* 渲染 */ }
        // 注意：不是 requireConsume(NotesFeatureViewModelKey) ——
        // 我们不暴露子 VM 的 key，子 VM 是 Shell 的实现细节。
    }
}
```

**这条约束的意义**：所有「子 Page 看屏幕的入口」仍然唯一（`FeedShellViewModelKey`）。换言之 M2 的「每屏一个 Shell」从字面"一个类"被泛化成"一个对外门面"——切片是实现细节。删一个子 VM、合并两个子 VM、把 `LiveTeaser` 整个拿掉，**外部代码不变**。

#### 跨切片通信的纪律

| 想做的事 | 怎么写 | 反例 |
| --- | --- | --- |
| 切片 A 改了状态，切片 B 要被动同步 | 在 Shell 的命令方法里同时改 A 和 B（如上面 `switchTab`） | A 的 VM 里直接调 `commentsVm.closeIfOpen()`——A 不该知道 B 存在 |
| 切片 A 想读 B 的当前状态做决定 | 在 Shell 里 `withState(b) { ... }` 取值，再下命令给 A | A 持有 B 的引用 |
| 一处用户操作要同时落到 A / B | 一律由 Shell 的命令方法承接 → 拆解为对 A / B 的两次方法调用 | Page 自己调 `a.foo(); b.bar()`——下次同样的事第二个调用顺序就会发散 |

> **本质**：子 VM 之间**不应该相互引用**，它们的所有交互都通过 Shell 编排。Shell 是这个屏幕"业务规则"的居所；切片是 Shell 调用的"领域工具"。

### 形态 B：分页 ViewModel（Per-Page VM）

**何时**：某个 Page 自身有**外部完全不关心的、纯局部**的状态（一个 form 的草稿、一段动画的进度、一个折叠组件的展开度）。

**做法**：用 [`by pageViewModel()`](../foundations/assemblekit/src/main/java/com/demo/foundations/assemblekit/PageViewModel.kt) 拿一个**只为这块 UI 服务**的 `MavericksViewModel`。它不进 Shell、不出现在任何 `PageContextKey` 上、其他 Page 看不到也不需要看到。

```kotlin
data class HeaderDraftState(val text: String = "") : MavericksState
class HeaderDraftViewModel(s: HeaderDraftState) : MavericksViewModel<HeaderDraftState>(s) {
    fun edit(t: String) = setState { copy(text = t) }
}

class FeedComposerHeaderPage : ViewPage() {
    private val draftVm: HeaderDraftViewModel by pageViewModel()       // 局部
    private val shell  : FeedShellViewModel get() = requireConsume(FeedShellViewModelKey)

    override fun onViewCreated(view: View) {
        draftVm.onEach(HeaderDraftState::text) { /* 渲染输入框 */ }
        view.findViewById<Button>(R.id.submit).setOnClickListener {
            // 提交时把"局部草稿"翻译成"屏幕级命令"
            withState(draftVm) { s -> shell.submitDraft(s.text) }
        }
    }
}
```

**纪律**：`pageViewModel()` 的状态**不可以被其他 Page 看到**。如果有兄弟 Page 也想读这块状态，说明它根本就不该是"局部"——把它升回 Shell（或某个 sub-VM），别用 bus / PageContext 把局部 VM 偷渡出去。

---

## 4. 反模式：水平切片（不要这样拆）

**做法**：按"层"拆——一个 `XxxStateViewModel` 只持 state，一个 `XxxLogicViewModel` 持业务逻辑，再来个 `XxxNavigationViewModel` 持导航。理论上很"职责分明"，实际是灾难：

| 问题 | 解释 |
| --- | --- |
| 协同模式跑偏 | 每个动作都要"读 A → 算 B → 写 C"，最后还要 Shell 帮你串。这就是把"分层架构"的层间耦合搬到了同一屏内部。 |
| 单元测试反而更难 | 业务逻辑需要 mock state VM；state VM 需要 mock logic VM。原本 1 个文件能跑完的测试拆成 3 个，每个都 4 行 setup。 |
| 状态原子性破坏 | 一次用户操作要在三个 VM 各一次 `setState`，订阅者会看到三个中间帧；要保证一致就得引入"协调者"——你又回到了一个大 VM。 |
| Mavericks 的 `withState` 失效 | `withState` 是单 VM 范围；跨 VM "读多个的当前快照"没有原子保证。 |

**正确的"分层"是 VM 内部的：** 把业务规则提取成 plain Kotlin 类，VM 是个薄的 Mavericks 适配层。这是常规面向对象重构，跟 MVI / Mavericks 无关。

```kotlin
// 业务规则不依赖 Mavericks
class FeedNotesUseCase(private val repo: FeedRepository) {
    suspend fun loadInitial(): List<Note> = repo.load()
    suspend fun like(id: String, current: List<Note>): List<Note> = repo.like(id)
}

// VM 是适配层
class FeedShellViewModel(
    initial: FeedShellState,
    private val notes: FeedNotesUseCase,
) : MavericksViewModel<FeedShellState>(initial) {
    fun refresh()           = suspend { notes.loadInitial() }.execute { copy(notes = it) }
    fun like(id: String)    = withState { s ->
        suspend { notes.like(id, s.notes() ?: emptyList()) }.execute { copy(notes = it) }
    }
}
```

---

## 5. 决策清单

走到这一步还在犹豫？按顺序回答：

1. **state 字段能不能用 data class 子聚合压下来？**
   - 能 → 改 state shape，**不动 VM 数量**。
   - 不能 → 往下。
2. **业务上是不是 2 个以上正交领域？**
   - 否 → 提取 UseCase / Repository 层，VM 文件本身简化；**不动 VM 数量**。
   - 是 → 形态 A（垂直切片 + Shell 门面）。
3. **这块状态是否真的只服务于一个 Page、没人想从外面看？**
   - 是 → 形态 B（`by pageViewModel()`），不进 Shell。
   - 否 → 它属于 Shell（或 Shell 下的某个 sub-VM），别走 B。
4. **想按"layer"拆（state / logic / navigation）？**
   - **不要**。回到第 1 步重新读。

---

## 6. 与 mvi-rules.md 的关系

| Rule | 拆 VM 后还得守 |
| --- | --- |
| M1 / M2 | 外部仍然只看见**一个**门面 VM (`XxxShellViewModelKey`)；sub-VM 是 Shell 的实现细节，不暴露独立 key。 |
| M3 | 子 Page 用 `shell.notes.onEach(...)` 也是 Mavericks selector；继续禁止裸 `collect` / `withState` 驱动 UI。 |
| M4 | 跨切片协同**必须**经过 Shell 的命令方法；不要让 Page 直接调 sub-VM A + sub-VM B 把编排逻辑泄到 UI 层。 |
| M5 | `Assembly.replace` 的触发条件仍然必须是 `viewModel.onEach(ShellState::flag)`（或某个 sub-VM 的 state，只要触发点在 Host 里）。 |
| M6 | 深层 widget 仍然 `view.requirePageContext().requireConsume(XxxShellViewModelKey)`——拿到 Shell 后再 `.notes`、`.comments`，**不要**给 sub-VM 单独申请 PageContextKey。 |

记住这条核心：**M2 的「一个 Shell」是「一个对外门面」，不是「一个类」**。

---

## 7. 何时回归（拆回去）

切片做了一阵子，发现：

- Shell 命令几乎全是 1:1 转发给某个 sub-VM；
- sub-VM 之间从未真正协同过；
- 子 Page 实际上只用一个 sub-VM；

——那就**把 sub-VM 合回 Shell**。它原本就只在你以为"会有协同"的预算里才值得存在。M2 的简单形态从来不是债务，是默认收益。
