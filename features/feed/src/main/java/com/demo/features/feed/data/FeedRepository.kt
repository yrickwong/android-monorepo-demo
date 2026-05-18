package com.demo.features.feed.data

import com.demo.features.feed.model.Note
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tiny in-memory fake repository. The real one would talk to network
 * + DB; for the AssembleKit demo we only care about *exposing a Flow
 * of items* so [com.demo.foundations.assemblekit.list.ListPage] has
 * something to subscribe to.
 *
 * Critically, this class has no Android dependencies — it's the kind
 * of plain object a real Feed module would `provide(...)` from its
 * Activity so the inner pages and ListPage rows can `consume(...)`
 * it without taking it through constructor parameters.
 */
class FeedRepository {

    private val _notes = MutableStateFlow(seed())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val refreshCounter = AtomicInteger(0)

    /**
     * Pretend we re-fetched from the server: bumps every note's id
     * suffix so DiffUtil shows visible reshuffling, and tweaks the
     * like counts so `areContentsTheSame` differs.
     */
    fun refresh() {
        val gen = refreshCounter.incrementAndGet()
        _notes.value = _notes.value.mapIndexed { idx, note ->
            note.copy(
                title = note.title.substringBefore(" #") + " #$gen",
                likes = note.likes + idx + 1,
            )
        }
    }

    fun likeOne(noteId: String) {
        _notes.value = _notes.value.map { note ->
            if (note.id == noteId) note.copy(likes = note.likes + 1) else note
        }
    }

    private fun seed(): List<Note> = listOf(
        Note(id = "n1", title = "AssembleKit ships v2", author = "framework-team", likes = 12),
        Note(id = "n2", title = "Mavericks 3 deep dive", author = "mvi-fans", likes = 27),
        Note(id = "n3", title = "RecyclerView still wins", author = "perf-team", likes = 4),
        Note(id = "n4", title = "When to use Fragments (rarely)", author = "afire", likes = 88),
        Note(id = "n5", title = "Modular Android in 2024", author = "platform", likes = 15),
        Note(id = "n6", title = "DSLs without the magic", author = "kotlin-news", likes = 33),
    )
}
