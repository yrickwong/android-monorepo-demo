package com.demo.features.feed

/**
 * The complete set of host-bus events Feed pages emit. Sealed so the
 * Activity's `hostBus.on { … }` site is exhaustive and so adding a new
 * event surfaces every consumer at compile time.
 */
internal sealed interface FeedEvent {

    /** Header pressed "refresh" — host re-fetches the underlying repo. */
    object RefreshRequested : FeedEvent

    /** Header pressed "toggle banner" — host swaps the assembly composition. */
    object ToggleBannerRequested : FeedEvent
}
