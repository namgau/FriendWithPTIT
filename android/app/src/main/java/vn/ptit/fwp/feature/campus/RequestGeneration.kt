package vn.ptit.fwp.feature.campus

import vn.ptit.fwp.core.model.CampusPost

/** Rejects results from an earlier refresh, tab, or dismissed sheet. */
internal class RequestGeneration {
    private var current = 0L
    fun next(): Long = ++current
    fun snapshot(): Long = current
    fun isCurrent(value: Long): Boolean = current == value
}

internal fun rollbackLike(current: CampusPost, before: CampusPost): CampusPost = current.copy(
    viewer = current.viewer.copy(liked = before.viewer.liked),
    counts = current.counts.copy(likes = before.counts.likes)
)

internal fun rollbackBookmark(current: CampusPost, before: CampusPost): CampusPost = current.copy(
    viewer = current.viewer.copy(bookmarked = before.viewer.bookmarked),
    counts = current.counts.copy(bookmarks = before.counts.bookmarks)
)
