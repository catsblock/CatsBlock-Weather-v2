package com.catsblock.weather.data

import com.catsblock.weather.ui.LatestSender
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsReactionTest {

    private fun article(
        likes: Int = 0,
        liked: Boolean = false,
        reactions: Map<String, Int> = emptyMap(),
        mine: String? = null
    ) = NewsArticle(
        id = "aaaaaaaaaaaaaaaa",
        title = "Storm warning",
        summary = "",
        source = "Source",
        publishedAt = 0L,
        imageUrl = null,
        url = "https://example.test/a",
        likes = likes,
        liked = liked,
        reactions = reactions,
        myReaction = mine
    )

    @Test
    fun likingAddsOneToTheTotal() {
        val result = article(likes = 4).withLike(true)
        assertTrue(result.liked)
        assertEquals(5, result.likes)
    }

    @Test
    fun unlikingRemovesOneFromTheTotal() {
        val result = article(likes = 4, liked = true).withLike(false)
        assertEquals(false, result.liked)
        assertEquals(3, result.likes)
    }

    @Test
    fun repeatingALikeKeepsTheTotal() {
        val result = article(likes = 4, liked = true).withLike(true)
        assertEquals(4, result.likes)
    }

    @Test
    fun unlikingNeverGoesBelowZero() {
        val result = article(likes = 0, liked = true).withLike(false)
        assertEquals(0, result.likes)
    }

    @Test
    fun choosingAReactionIncrementsItsOwnCount() {
        val result = article().withReaction(Reaction.StarStruck.emoji)
        assertEquals(Reaction.StarStruck.emoji, result.myReaction)
        assertEquals(1, result.countOf(Reaction.StarStruck))
        assertEquals(0, result.countOf(Reaction.Clapping))
    }

    @Test
    fun switchingAReactionMovesTheCount() {
        val start = article(reactions = mapOf(Reaction.StarStruck.emoji to 3), mine = Reaction.StarStruck.emoji)
        val result = start.withReaction(Reaction.ThumbsUp.emoji)
        assertEquals(Reaction.ThumbsUp.emoji, result.myReaction)
        assertEquals(2, result.countOf(Reaction.StarStruck))
        assertEquals(1, result.countOf(Reaction.ThumbsUp))
    }

    @Test
    fun clearingAReactionRemovesIt() {
        val start = article(reactions = mapOf(Reaction.Crying.emoji to 1), mine = Reaction.Crying.emoji)
        val result = start.withReaction(null)
        assertNull(result.myReaction)
        assertEquals(0, result.countOf(Reaction.Crying))
    }

    @Test
    fun repeatingTheSameReactionChangesNothing() {
        val start = article(reactions = mapOf(Reaction.Crying.emoji to 1), mine = Reaction.Crying.emoji)
        val result = start.withReaction(Reaction.Crying.emoji)
        assertEquals(1, result.countOf(Reaction.Crying))
        assertEquals(Reaction.Crying.emoji, result.myReaction)
    }

    @Test
    fun removingAReactionNeverGoesBelowZero() {
        val start = article(reactions = mapOf(Reaction.Lightning.emoji to 0), mine = Reaction.Lightning.emoji)
        val result = start.withReaction(null)
        assertEquals(0, result.countOf(Reaction.Lightning))
    }

    @Test
    fun visibleReactionsKeepTheSelectedReaction() {
        val result = article(reactions = mapOf(Reaction.Grinning.emoji to 2), mine = Reaction.Clapping.emoji)
        assertEquals(listOf(Reaction.Grinning, Reaction.Clapping), result.visibleReactions)
    }

    @Test
    fun everyReactionResolvesFromItsEmoji() {
        Reaction.entries.forEach { reaction ->
            assertEquals(reaction, Reaction.of(reaction.emoji))
        }
        assertNull(Reaction.of("🔥"))
    }

    @Test
    fun rapidTapsCollapseIntoTheLatestRequest() = runBlocking {
        val worker = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val received = ArrayList<Int>()
            val firstStarted = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val finished = CompletableDeferred<Unit>()
            val sender = LatestSender<Int>(worker) { value ->
                received += value
                if (value == 1) {
                    firstStarted.complete(Unit)
                    release.await()
                }
                if (value == 3) finished.complete(Unit)
            }
            sender.submit(1)
            firstStarted.await()
            sender.submit(2)
            sender.submit(3)
            release.complete(Unit)
            finished.await()
            assertEquals(listOf(1, 3), received)
        } finally {
            worker.cancel()
        }
    }
}
