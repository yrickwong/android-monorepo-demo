package com.demo.features.feed

import com.demo.foundations.assemblekit.local.pageContextKey

/**
 * Single context key the Feed module exposes: the shell ViewModel.
 *
 * Why this is the *only* key now (down from "repository + click
 * handler + …"):
 *  - The ViewModel is the single source of truth. Anything a Page used
 *    to need from the repo (current list, refresh trigger, like
 *    callback) is now a method on the VM — so one consume hop is
 *    enough.
 *  - Pages have no business consuming the repository directly. That
 *    would put them back on the "two sources of truth" treadmill —
 *    raw `collect(repo.notes)` instead of `viewModel.onAsync(...)`.
 *    AGENTS.md Rule 2 explicitly forbids this; not exposing the repo
 *    key turns the rule into a structural impossibility.
 *  - Handlers like "like this note" are now `viewModel.likeOne(id)` at
 *    the call site, so we don't need a separate `NoteClickKey`
 *    function indirection either.
 *
 * Identity matters: this is an `internal val` singleton so two
 * unrelated modules can't accidentally cross-pollinate locals by
 * choosing the same name.
 */
internal val FeedShellViewModelKey =
    pageContextKey<FeedShellViewModel>("feed.shellViewModel")
