package com.demo.features.feed.model

/**
 * The single domain type the Feed demo cares about. Kept dumb on
 * purpose: equality of [id] is what powers DiffUtil through
 * `ItemBinder.areItemsTheSame`, the rest fields fall under
 * `areContentsTheSame` via data-class `equals`.
 */
data class Note(
    val id: String,
    val title: String,
    val author: String,
    val likes: Int,
)
