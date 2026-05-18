package com.demo.features.feed.page

import com.demo.features.feed.binder.NoteItemBinder
import com.demo.features.feed.model.Note
import com.demo.foundations.assemblekit.list.ListPage
import kotlinx.coroutines.flow.Flow

/**
 * The actual feed body. Reuses the framework's [ListPage] verbatim —
 * the only "demo-specific" bits are the item type (`Note`) and the
 * binder (`NoteItemBinder`). Layout manager, diffing, lifecycle and
 * subscription teardown are all owned by the framework.
 */
internal class FeedListPage(
    notes: Flow<List<Note>>,
) : ListPage<Note>(itemsFlow = notes, itemBinder = NoteItemBinder)
