package com.demo.features.feed.data

import com.demo.features.feed.model.Note
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tiny in-memory fake repository.
 *
 * Design rule (post Mavericks migration): a repository **never owns
 * observable state**. It exposes plain `suspend` functions that return
 * the current value; the [com.demo.features.feed.FeedShellViewModel]
 * is the single source of truth and writes everything into
 * [com.demo.features.feed.FeedShellState] via `setState { copy(...) }`.
 *
 * Why we stripped the old `MutableStateFlow<List<Note>>`:
 *  - Two sources of truth (repo + Mavericks state) drift; whoever
 *    forgets to mirror loses the update.
 *  - Pages used to `collectLatest(repo.notes)` directly, which is the
 *    exact anti-pattern AGENTS.md Rule 2 calls out — bypassing the
 *    `Async<T>` lifecycle hides loading/error states.
 *  - Mocking a `suspend` function in tests is trivial; faking a hot
 *    Flow with realistic timing isn't.
 *
 * If a future feature genuinely needs a push-based source (e.g. socket
 * notifications), it should surface as a `Flow<Event>` consumed *by
 * the ViewModel*, not by Pages.
 */
internal class FeedRepository {

    private val refreshCounter = AtomicInteger(0)
    private val mutableSnapshot = seed().toMutableList()

    /**
     * Pretend we re-fetched from the server.
     *
     * Returns the new list rather than mutating shared state behind the
     * ViewModel's back. The `delay` lets the Mavericks `Async` cycle
     * actually show `Loading` for a tick — useful in the demo to prove
     * that PageViewModels expose the loading state to UI.
     */
    suspend fun load(): List<Note> {
        delay(LOAD_LATENCY_MS)
        val gen = refreshCounter.incrementAndGet()
        val updated = mutableSnapshot.mapIndexed { idx, note ->
            note.copy(
                title = note.title.substringBefore(" #") + " #$gen",
                likes = note.likes + idx + 1,
            )
        }
        mutableSnapshot.clear()
        mutableSnapshot.addAll(updated)
        return updated.toList()
    }

    /**
     * Like operation. Synchronous because the demo doesn't model the
     * network round-trip; in a real app this would `suspend` and the
     * ViewModel would wrap it in `execute {}` to surface in-flight
     * state per-row.
     */
    fun like(noteId: String): List<Note> {
        val updated = mutableSnapshot.map { note ->
            if (note.id == noteId) note.copy(likes = note.likes + 1) else note
        }
        mutableSnapshot.clear()
        mutableSnapshot.addAll(updated)
        return updated.toList()
    }

    private fun seed(): List<Note> = listOf(
        Note(id = "n1", title = "AssembleKit ships v2", author = "framework-team", likes = 12),
        Note(id = "n2", title = "Mavericks 3 deep dive", author = "mvi-fans", likes = 27),
        Note(id = "n3", title = "RecyclerView still wins", author = "perf-team", likes = 4),
        Note(id = "n4", title = "When to use Fragments (rarely)", author = "afire", likes = 88),
        Note(id = "n5", title = "Modular Android in 2024", author = "platform", likes = 15),
        Note(id = "n6", title = "DSLs without the magic", author = "kotlin-news", likes = 33),
    )

    companion object {
        /**
         * 250ms is just enough to let the UI observe `Loading` without
         * making the demo feel sluggish. Keep in sync with any UI tests
         * that assert spinner visibility windows.
         */
        private const val LOAD_LATENCY_MS = 250L
    }
}
