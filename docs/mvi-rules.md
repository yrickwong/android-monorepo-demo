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
- Pages extend [`ViewPage`](../foundations/assemblekit/src/main/java/com/demo/foundations/assemblekit/ViewPage.kt)
  today; once the Compose module ships, `ComposablePage` will be the
  Compose equivalent. The XML-vs-Compose choice is **per page**, never
  framework-wide.
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
└── binder/NoteItemBinder.kt  ← row → ctx.requireConsume(FeedShellViewModelKey).likeOne(id)
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

If all boxes are ticked, the PR conforms. If any is unchecked, justify in
the PR description.
