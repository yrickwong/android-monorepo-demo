package com.demo.features.feed.model

/**
 * The single domain type the Feed demo cares about. Kept dumb on
 * purpose: equality of [id] is what powers DiffUtil through
 * `ItemBinder.areItemsTheSame`, the rest fields fall under
 * `areContentsTheSame` via data-class `equals`.
 *
 * `tags` was added to support the v2 demo of "nested RecyclerView whose
 * inner ViewHolder reaches the Shell VM via `findPageContext()`".
 * Each tag chip is its own RecyclerView ViewHolder inside a horizontal
 * carousel inside the row, three layers below the parent Page —
 * exactly the depth that motivates the ViewTree lookup pattern.
 */
data class Note(
    val id: String,
    val title: String,
    val author: String,
    val likes: Int,
    val tags: List<String> = emptyList(),
)
