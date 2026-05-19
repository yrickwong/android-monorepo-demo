# MVI Rules — assembly + Mavericks is the only sanctioned shape for business pages

This document is the **source of truth** for how business features in this
repo manage state. It is referenced by [`AGENTS.md` § Rule 2](../AGENTS.md#rule-2-all-business-features-ship-as-assembly--mavericks),
and the rules in here are enforced both by human code review and by docs-sync.

> **TL;DR** — every business page is **(a)** composed by AssembleKit
> (`assemble { … }` of Pages mounted to ViewGroup slots) and **(b)** has
> exactly one Mavericks ViewModel as its single source of truth. Anything
> that looks like "state living somewhere else" — `MutableStateFlow` in a
> Page, `var` on the host, `repo.someHotFlow.collect` in `onViewCreated`,
> event-bus messages used as a command channel — is rejected on sight in
> CR. The Feed module is the canonical reference implementation; copy that
> shape.

---

## Why this rule exists

We have already lived through (and removed) every one of these failure
modes in earlier iterations of this codebase:

| Failure | What broke |
| --- | --- |
| Two sources of truth (Repository `MutableStateFlow` + Page-local mirror) | Drifted on first config change; "phantom like" bug |
| State as `var` on Activity (`var bannerVisible: Boolean`) | Lost on process death restoration; banner reappeared/vanished randomly |
| Event-bus commands (`hostBus.send(RefreshRequested)`) | Edge-triggered → replay on restoration was impossible; required a parallel "current snapshot" field |
| Page-driven `assembly.replace` via a click listener | Bypassed lifecycle ordering; old Page sometimes saw post-detach events |
| Repository injected into PageContext (`provides(RepoKey, repo)`) | Repo became part of the **page contract** instead of a VM implementation detail; swap was a multi-file refactor |

The rule below makes each of these failures **structurally impossible** —
not "caught in review", not "trapped in tests", *impossible*.

---

## The five non-negotiable rules

### Rule M1 — Hosts and Pages use the v2 abstractions

- Hosts extend [`PageHostActivity`](../foundations/assemblekit/src/main/java/com/demo/foundations/assemblekit/PageHostActivity.kt)
  or implement [`PageHost`](../foundations/assemblekit/src/main/java/com/demo/foundations/assemblekit/PageHost.kt).
- Pages extend one of the three Page flavours, and the choice is
  **per page**, never framework-wide:
  - [`ViewPage`](../foundations/assemblekit/src/main/java/com/demo/foundations/assemblekit/ViewPage.kt)
    — classic XML, synchronous inflate (the default).
  - [`AsyncViewPage`](../foundations/assemblekit/src/main/java/com/demo/foundations/assemblekit/AsyncViewPage.kt)
    — XML, but inflated off the main thread via `AsyncLayoutInflater`;
    see [`foundations/assemblekit/README.md` § 5.1](../foundations/assemblekit/README.md#51-asyncviewpagesrcmainjavacomdemofoundationsassemblekitasyncviewpagekt重布局异步-inflate)
    for the "measure before you switch" guidance.
  - [`ComposablePage`](../foundations/assemblekit-compose/src/main/java/com/demo/foundations/assemblekit/compose/ComposablePage.kt)
    — Jetpack Compose; lives in the optional sibling module
    [`:foundations:assemblekit-compose`](../foundations/assemblekit-compose/README.md)
    so XML-only feature modules pay zero Compose toolchain cost.
  All three obey the same `Page` lifecycle / `PageContext` / Shell VM
  contract; the Compose flavour additionally re-publishes `PageContext`
  as a `CompositionLocal` (see Rule M6).
- A business feature module **must not** ship a class that extends
  `AppCompatActivity` directly without going through `PageHostActivity`.

### Rule M2 — One Shell ViewModel per page

- Every business page has exactly **one** Activity-scoped Mavericks
  ViewModel, which we call its **Shell ViewModel**. It owns the screen's
  state, holds the repositories, and exposes commands.
- Construction pattern:
  ```kotlin
  data class XxxShellState(
      val items: Async<List<Item>> = Uninitialized,
      val ...: ... ,
  ) : MavericksState

  class XxxShellViewModel(
      initialState: XxxShellState,
      private val repo: XxxRepository,
  ) : MavericksViewModel<XxxShellState>(initialState) {
      companion object : MavericksViewModelFactory<XxxShellViewModel, XxxShellState> {
          override fun create(vc: ViewModelContext, s: XxxShellState) =
              XxxShellViewModel(s, XxxRepository())
      }
  }
  ```
- Host creates it in `ActivityViewModelContext` (so it survives config
  changes and is exposed as a single instance):
  ```kotlin
  viewModel = MavericksViewModelProvider.get(
      viewModelClass = XxxShellViewModel::class.java,
      stateClass     = XxxShellState::class.java,
      viewModelContext = ActivityViewModelContext(this, null),
      key            = "xxx_shell",
  )
  ```
- Host exposes it to the assembly in **one** place at assembly scope:
  ```kotlin
  val XxxShellViewModelKey = pageContextKey<XxxShellViewModel>("xxx.shellViewModel")
  ...
  assemble {
      provides(XxxShellViewModelKey, viewModel)
      +XxxHeaderPage() at R.id.header_slot
      ...
  }
  ```
- Sub-Pages and `ItemBinder`s consume it:
  ```kotlin
  val vm = requireConsume(XxxShellViewModelKey)
  ```
- **Per-Page transient local state** (e.g. a draft form value that nobody
  outside that Page cares about) may use the Page-local
  [`PageViewModel<S>`](../foundations/assemblekit/src/main/java/com/demo/foundations/assemblekit/PageViewModel.kt)
  delegate; it is also a `MavericksViewModel<S>`, just keyed per-Page so
  multiple instances of the same Page type don't share state. Never use
  raw `MutableStateFlow` / `LiveData` / `var` for view state in a Page.
- **When the single Shell VM becomes too big** (15+ state fields, 20+
  commands, 400+ lines, or 3+ orthogonal sub-domains in one screen), do
  not silently split state across loose objects or per-Page mirrors —
  read [`docs/sharding-shell-vm.md`](sharding-shell-vm.md) first. The
  short version: M2's "one Shell VM" is **"one outward-facing facade"**,
  not "one class". You may shard into sub-VMs *behind* the facade
  (vertical / per-feature slicing), but sub-Pages and deep widgets must
  still see exactly one `XxxShellViewModelKey`; sub-VMs are an
  implementation detail of the Shell and must not be published as their
  own PageContextKeys. Horizontal slicing (a State-VM + a Logic-VM + a
  Nav-VM) is **not** allowed.

### Rule M3 — Rendering uses Mavericks selectors only

- The host's `MavericksView.invalidate()` is **always empty** (`= Unit`).
  Rendering is wired by **targeted selectors**:
  ```kotlin
  viewModel.onEach(XxxShellState::isLoading) { ... }
  viewModel.onAsync(XxxShellState::items,
      onSuccess = { renderItems(it) },
      onFail    = { showError(it) },
  )
  viewModel.onEach(XxxShellState::a, XxxShellState::b) { a, b -> ... }
  ```
- Do **not** spin your own `viewModelScope.launch { state.collect { ... } }`
  to feed the UI — you'll lose Mavericks' equality-based de-duplication,
  lifecycle scoping, and `Async` typing.
- Do **not** call `withState { ... }` to drive UI. `withState` is for
  computing inside the VM (e.g. transitioning state based on current
  state); UI subscribes via `onEach` / `onAsync`.
- `MavericksView.onEach` is **lifecycle-scoped to the subscribing object**
  via `subscriptionLifecycleOwner`. For our Pages that means the Page's
  own `Lifecycle`; `performDetach` pushes `ON_DESTROY` and the
  subscription is cancelled — so subscriptions you set up in
  `onViewCreated` cannot leak past a host-driven `assembly.replace { }`.

### Rule M4 — Side effects are method calls on the VM, not events

- Pages and Binders express user intent by calling **typed methods** on
  the Shell VM:
  ```kotlin
  // Header page
  binding.btnRefresh.setOnClickListener { vm.refresh() }
  // Row binder
  view.setOnClickListener { vm.likeOne(item.id) }
  ```
- The VM is the only place that decides *how* to honor the intent (debounce,
  optimistic update, conflict resolution).
- `ScopedEventBus` / `ScopedCommandBus` (host-bus + assembly-bus) **are
  still allowed**, but **only for transient fire-and-forget signals** that
  genuinely have no state representation: toasts, "scroll to top", a
  navigation request out of this screen. Heuristic: if you ever feel the
  need to also keep a `var latestThing: T?` somewhere to "remember what
  the last event was", you're using the bus for state — stop, put it on
  `MavericksState`, and read it with `onEach`.

### Rule M5 — Structural recomposition is host-driven AND state-driven

- `assembly.replace { … }` may **only** be called from the host. Pages
  cannot reach the `Assembly` handle — that's by design.
- The **trigger** for `replace` must be a `viewModel.onEach(State::flag)`
  on a host-owned state field, not a click listener and not a bus event.
  ```kotlin
  viewModel.onEach(FeedShellState::showBanner) { showBanner ->
      if (feedAssembly.matchesBannerState(showBanner)) return@onEach
      installAssembly(showBanner)
  }
  ```
- Tag the current composition with a sentinel local
  (`provides(FlagKey, currentValue)`) so the host can detect "the
  composition I just installed already matches this state" and skip the
  no-op `replace`; without this guard, the initial `onEach` replay would
  trigger an infinite `onEach → replace → onEach` cycle.
- Configuration change and process-death restoration are automatic:
  Mavericks restores the state, `onEach` re-fires with the restored
  value, `installAssembly` rebuilds the right composition. You write zero
  extra code for this.

### Rule M6 — Deep custom views reach the Shell VM via the view tree, never via constructor / DI

The temptation: a `NoteActionBar` lives 3 layers under a row, and an
inner-RecyclerView `TagChipViewHolder` lives 5 layers down. The naive
fix is to drill the `FeedShellViewModel` through every intermediate
constructor / setter so the deep view can call it. That is **banned**.

- A reusable custom view must accept **data only** through its public
  surface (constructor / `bind()` / setters). Anything behavioural —
  the Shell VM, the analytics tracker, theme tokens — comes from the
  **view tree** via
  [`view.requirePageContext()`](../foundations/assemblekit/src/main/java/com/demo/foundations/assemblekit/ViewTreePageContext.kt)
  / [`view.findPageContext()`](../foundations/assemblekit/src/main/java/com/demo/foundations/assemblekit/ViewTreePageContext.kt).
- The framework stamps the `PageContext` for you. You **never** call
  `setPageContext` by hand in product code:
  - [`Page.performAttach`](../foundations/assemblekit/src/main/java/com/demo/foundations/assemblekit/Page.kt)
    stamps every Page's root view at attach time and clears it on
    detach.
  - [`ListPage.BinderAdapter.onCreateViewHolder`](../foundations/assemblekit/src/main/java/com/demo/foundations/assemblekit/list/ListPage.kt)
    stamps every row's `itemView`, so the parent Page's context is
    reachable from every descendant of every row (including
    nested-RecyclerView ViewHolders).
- Pick the right severity for the lookup:
  - `requirePageContext()` — mandatory production wiring; throws with
    a helpful pointer if the view was inflated outside any Page.
    Use this in widgets that are *only* meant to live inside an
    AssembleKit screen.
  - `findPageContext()` — nullable; use it in widgets whose layout
    might also be rendered in previews / snapshot tests / unit
    fixtures, so the bare inflate degrades gracefully.
- **Compose equivalent.** When the widget is a `@Composable` hosted
  inside a [`ComposablePage`](../foundations/assemblekit-compose/src/main/java/com/demo/foundations/assemblekit/compose/ComposablePage.kt),
  the same `PageContext` is re-published as a `CompositionLocal`. Use
  [`composeRequireConsume(key)`](../foundations/assemblekit-compose/src/main/java/com/demo/foundations/assemblekit/compose/LocalPageContext.kt)
  / [`composeConsume(key)`](../foundations/assemblekit-compose/src/main/java/com/demo/foundations/assemblekit/compose/LocalPageContext.kt)
  instead of `view.requirePageContext()`. The rule is identical: never
  pass the Shell VM through `@Composable` parameters, never reach into
  a DI container — go through the page-scoped `LocalPageContext`. The
  resolution chain (`pageLocal → assemblyLocal → hostLocal`) is the
  same one the View tree uses, because `ComposablePage` bridges the
  *same* `PageContext` instance into composition.
- The chain is `pageLocal → assemblyLocal → hostLocal`, so a Shell VM
  provided once with `provides(XxxShellViewModelKey, vm)` at assembly
  scope is automatically reachable from *every* descendant of *every*
  Page in that assembly — View tree or Compose tree alike. There is no
  per-row plumbing.

Why this is preferred over taking the VM in the constructor:
- XML inflation cannot pass non-`Context/AttributeSet` arguments. The
  "drill the VM in" workaround forces a `setViewModel(vm)` setter,
  which forces the binder to call it on every widget, which forces
  the widget to forward it to every inner sub-widget. That is exactly
  the parameter-drill cascade we are eliminating.
- A widget that hard-codes `FeedShellViewModel` in its API can only
  ever live in Feed. A widget that resolves the VM via a typed
  PageContextKey is portable to any AssembleKit screen that provides
  the same key — turning "reusable" from an aspiration into a
  default.

The canonical example is the trio
[`NoteActionBar`](../features/feed/src/main/java/com/demo/features/feed/widget/NoteActionBar.kt),
[`RelatedTagsCarousel`](../features/feed/src/main/java/com/demo/features/feed/widget/RelatedTagsCarousel.kt),
and [`FeedFooterPage`](../features/feed/src/main/java/com/demo/features/feed/page/FeedFooterPage.kt):
clicks 3 / 5 layers deep land on `FeedShellState.lastShared` /
`lastTag`, and the footer (mounted in a *different* slot) renders
them — proving the deep lookup resolves the *same* shell VM, not
some accidental row-scoped instance.

---

## <a name="anti-patterns"></a>Anti-patterns (CR auto-reject)

Any of the following is a CR -1 with a link to this file. If you have a
genuine reason to violate one, explain it in the PR description and ping
the tech lead.

| # | Bad shape | Why it's banned | What to do instead |
| --- | --- | --- | --- |
| A1 | `private val _foo = MutableStateFlow(...)` in a Page or in a `PageHostActivity` that is using AssembleKit | Two sources of truth; will drift from VM state | Put it on `MavericksState`, read with `onEach` |
| A2 | `lifecycleScope.launch { repo.someHotFlow.collect { render(it) } }` in `Page.onViewCreated` | Bypasses the VM; can't be observed or tested in isolation | Inject repo into Shell VM; expose derived `Async<T>`; `viewModel.onAsync(...)` |
| A3 | `private var bannerVisible = false` on a host plus `assembly.replace` from a click listener | Edge-triggered → lost on rotation/process death | `MavericksState.showBanner: Boolean` + `viewModel.onEach(::showBanner)` |
| A4 | `provides(SomeRepositoryKey, repo)` in `assemble {}` | Repo becomes part of the Page contract; swap is multi-file refactor; tests must mock context locals | Repo is a private constructor param of the Shell VM; `provides(XxxShellViewModelKey, vm)` instead |
| A5 | `hostBus.on<SomeRequest>` used as a synchronous "do this for me" channel (and especially if you also keep a snapshot field) | Edge-triggered; not replayed on restoration; encourages "state via events" | A method on the Shell VM; bus only for transient toasts/navigation |
| A6 | `withState { vm -> render(vm.foo) }` to draw UI | Single-shot read; misses subsequent updates; will be silently stale | `viewModel.onEach(State::foo) { render(it) }` |
| A7 | `class FooPage(val flow: Flow<...>) : Page` where the flow is `repo.someHotFlow` | Same as A2, just hidden in a constructor | Derive in host: `viewModel.stateFlow.map { it.x }.distinctUntilChanged()` and pass that |
| A8 | A non-empty `MavericksView.invalidate()` body in a host that also uses `onEach` | Two render paths; one will be slower/buggier; people will argue over which is canonical | Keep `invalidate() = Unit`; use `onEach`/`onAsync` exclusively |
| A9 | A Page calling `assembly.replace` (via reflection / by stashing the handle) | Breaks "host decides structure"; defeats restoration; makes ordering of detach/attach non-deterministic | Send a method call to the Shell VM; let the host's `onEach` re-trigger `installAssembly` |
| A10 | Mavericks VM exposed as a global singleton or DI-scoped object instead of per-host | Wrong lifetime; cross-page leaks; impossible to reason about restoration | `ActivityViewModelContext(this, null)` + `provides(...)` in assembly |
| A11 | `class NoteActionBar(ctx, attrs, val vm: FeedShellViewModel)` — Shell VM passed in via constructor / setter to a reusable widget | Hard-couples the widget to one feature; breaks XML inflation; forces parameter-drilling through every intermediate adapter / binder | `view.requirePageContext().requireConsume(FeedShellViewModelKey)` inside the click handler — see Rule M6 |
| A12 | `KoinJavaComponent.get<FeedShellViewModel>()` (or `Hilt`, or any DI lookup) from inside a custom view to reach the Shell VM | DI is global / app-scoped; you get *some* live instance, not "the VM of *this* host". Also a silent escape hatch around MVI — the view can now reach into a repo and bypass the VM. | `view.requirePageContext().requireConsume(XxxShellViewModelKey)` — page-scoped, type-checked, and the only path is through the Shell VM. See "Why not Koin/Hilt" below. |

---

## <a name="why-not-koin"></a>Why not Koin / Hilt / Dagger for this problem

A frequent question: "we already have a DI container in lots of
projects — why invent `findPageContext` instead of letting widgets
ask the container for the Shell VM?" Three reasons, in order of how
hard they bite.

### 1. Scope mismatch (the structural reason)

A DI container's natural unit is the **app** (singleton scope) or at
best the **Activity** (Activity scope, if you opt in to per-Activity
subcomponents). `PageContext` is a **page** — bounded by an
`assemble { … }` block which can come and go several times during the
Activity's life via `assembly.replace { }`.

If the chip widget asks Koin for `FeedShellViewModel`, Koin's only
honest answer is *some* live instance. With two simultaneously open
Feed surfaces (e.g. a master/detail tablet layout) you have two
`FeedShellViewModel`s, but Koin will hand the chip whichever one was
registered last — silently, with no compile error. View-tree lookup
hands the chip *the VM whose Page actually owns the row it lives in*,
because resolution walks the actual parent chain.

### 2. MVI escape hatch (the cultural reason)

Once `Koin.get<FeedRepository>()` works in any view, it works
**everywhere**. The "view reaches into the repo, computes its own
state, mutates the world" anti-pattern (A2, A4) becomes mechanically
easy. The MVI invariant — *all mutation flows through the Shell VM*
— stops being structurally enforced; it becomes "we trust everyone
to do the right thing", which is to say not enforced at all.

`view.requirePageContext().requireConsume(XxxShellViewModelKey)`
*forces* the view to talk to the Shell VM. The repository is private
to the VM's constructor and there is no key for it, by design.

### 3. Lifetime and teardown (the operational reason)

When `assembly.replace { }` rebuilds the composition, the framework
clears the `PageContext` tag on every detached Page root in
`performDetach`. Any view still cached externally (a row pool, a
screenshot util) sees `findPageContext() == null` and degrades
politely. A DI container has no equivalent — it keeps handing out
the same `FeedShellViewModel` reference long after the surface that
produced it has gone, which is how "ghost" subscriptions and
"updates render into a torn-down view" bugs are born.

### Where DI still earns its keep

Nothing above argues against DI in general — only against using it
as a *replacement* for the page-scoped lookup. Genuinely global
services (an HTTP client factory, a JSON parser, a feature-flag
gate) are exactly what a DI container is good at; route those
through the VM's constructor (or via the `MavericksViewModelFactory`)
and keep the view tree out of it.

---

## Reference implementation: `:features:feed`

The Feed module is the **canonical** assembly + Mavericks page. When in
doubt, mirror its shape. All file references below are clickable.

```
features/feed/src/main/java/com/demo/features/feed/
├── FeedActivity.kt           ← host: creates VM, drives structural replace
├── FeedShellState.kt         ← MavericksState (notes: Async<...>, showBanner)
├── FeedShellViewModel.kt     ← MavericksViewModel + factory (owns repo)
├── FeedKeys.kt               ← FeedShellViewModelKey only
├── data/FeedRepository.kt    ← pure data source (no flow, no state)
├── page/FeedHeaderPage.kt    ← consume VM; buttons call vm.refresh()/vm.toggleBanner()
├── page/FeedFooterPage.kt    ← consume VM; viewModel.onEach(::notes) for Loading/Fail/Success
├── page/FeedListPage.kt      ← ListPage<Note>(notes = derived flow)
├── page/FeedBannerPage.kt    ← static; mounted only when showBanner = true
├── binder/NoteItemBinder.kt  ← row → ctx.requireConsume(FeedShellViewModelKey).likeOne(id)
└── widget/
    ├── NoteActionBar.kt          ← reusable; depth-3 view, requirePageContext() at click time
    └── RelatedTagsCarousel.kt    ← reusable; inner RV chips at depth 5, findPageContext()
```

A 60-second walkthrough lives in
[`docs/architecture.md` § AssembleKit v2 — Feed 走读](architecture.md#assemblekit-v2列表上下文多槽位host-驱动-replace).

### What we explicitly removed in the Feed → Mavericks migration

| Before | After |
| --- | --- |
| `FeedRepositoryKey: PageContextKey<FeedRepository>` | Gone. Repository is private to the Shell VM. |
| `NoteClickKey: PageContextKey<(Note) -> Unit>` | Gone. Binders call `vm.likeOne(id)`. |
| `FeedEvent` sealed class + `hostBus.send/on` | Gone. Buttons call `vm.refresh()` / `vm.toggleBanner()`. |
| `repository.notes: StateFlow<List<Note>>` (hot, owned by repo) | Gone. Repo is `suspend fun load(): List<Note>` + `fun like(id): List<Note>`. The flow lives in `FeedShellViewModel.stateFlow`. |
| `var bannerVisible: Boolean` on Activity | Gone. `FeedShellState.showBanner: Boolean`. |

That is the shape every new business feature should arrive in.

---

## Quick checklist before opening a PR for a new page

- [ ] Host extends `PageHostActivity` and implements `MavericksView` (with
      `invalidate() = Unit`).
- [ ] Exactly one `XxxShellViewModel : MavericksViewModel<XxxShellState>`,
      created in `ActivityViewModelContext`, with `companion object :
      MavericksViewModelFactory` if it takes non-state constructor params.
- [ ] One `XxxShellViewModelKey = pageContextKey<XxxShellViewModel>(...)`
      at module top level, `provides(...)` it in `assemble {}` at the host.
- [ ] All Pages / Binders use `requireConsume(XxxShellViewModelKey)`.
      They do not own state, do not call `collect` on repository flows,
      do not hold `var` view fields.
- [ ] All rendering wired by `viewModel.onEach(...)` / `viewModel.onAsync(...)`.
      No `viewModelScope.launch { stateFlow.collect }`, no `withState`-for-render.
- [ ] All user intents are method calls on the VM.
- [ ] If you use `assembly.replace { }`: the trigger is a `viewModel.onEach`
      on a state field, the call is in the host, and there is a marker
      local + matches-helper to break the replay loop.
- [ ] Any reusable custom view that needs the Shell VM gets it via
      `view.requirePageContext().requireConsume(XxxShellViewModelKey)`
      — **never** via constructor / setter parameter, **never** via a
      DI container. The view's public surface is data only.

If all boxes are ticked, the PR conforms. If any is unchecked, justify in
the PR description.
