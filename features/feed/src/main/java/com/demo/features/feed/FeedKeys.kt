package com.demo.features.feed

import com.demo.features.feed.data.FeedRepository
import com.demo.features.feed.model.Note
import com.demo.foundations.assemblekit.local.pageContextKey

/**
 * Top-level singletons that act as the context keys for everything the
 * Feed Assembly exposes to its children.
 *
 * Keeping them at module-package scope (`internal val`) means:
 *  - they're shared by every Page / Binder inside `:features:feed`
 *    without leaking through the module boundary;
 *  - tests can re-import them to provide a fake repo in a mock host.
 *
 * Identity matters: each `PageContextKey<T>` is a distinct instance, so
 * two unrelated modules accidentally picking the same `name` cannot
 * cross-pollinate each other's locals.
 */
internal val FeedRepositoryKey = pageContextKey<FeedRepository>("feed.repository")

/**
 * Click handler exposed to rows. Demonstrates "provide a behaviour, not
 * just data": the binder calls back into something the Assembly owns,
 * without needing to know what concrete object lives behind it.
 */
internal val NoteClickKey = pageContextKey<(Note) -> Unit>("feed.noteClick")
