package com.catsblock.weather.data

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
        val result = article(likes = 4).optimisticLike()
        assertTrue(result.liked)
        assertEquals(5, result.likes)
    }

    @Test
    fun unlikingRemovesOneFromTheTotal() {
        val result = article(likes = 4, liked = true).optimisticLike()
        assertEquals(false, result.liked)
        assertEquals(3, result.likes)
    }

    @Test
    fun unlikingNeverGoesBelowZero() {
        val result = article(likes = 0, liked = true).optimisticLike()
        assertEquals(0, result.likes)
    }

    @Test
    fun addingAReactionIncrementsItsOwnCount() {
        val result = article().optimisticReaction(Reaction.StarStruck.emoji)
        assertEquals(Reaction.StarStruck.emoji, result.myReaction)
        assertEquals(1, result.countOf(Reaction.StarStruck))
        assertEquals(0, result.countOf(Reaction.Clapping))
    }

    @Test
    fun changingAReactionMovesTheCount() {
        val start = article(reactions = mapOf(Reaction.StarStruck.emoji to 3), mine = Reaction.StarStruck.emoji)
        val result = start.optimisticReaction(Reaction.ThumbsUp.emoji)
        assertEquals(Reaction.ThumbsUp.emoji, result.myReaction)
        assertEquals(2, result.countOf(Reaction.StarStruck))
        assertEquals(1, result.countOf(Reaction.ThumbsUp))
    }

    @Test
    fun selectingTheSameReactionRemovesIt() {
        val start = article(reactions = mapOf(Reaction.Crying.emoji to 1), mine = Reaction.Crying.emoji)
        val result = start.optimisticReaction(Reaction.Crying.emoji)
        assertNull(result.myReaction)
        assertEquals(0, result.countOf(Reaction.Crying))
    }

    @Test
    fun removingAReactionNeverGoesBelowZero() {
        val start = article(reactions = mapOf(Reaction.Lightning.emoji to 0), mine = Reaction.Lightning.emoji)
        val result = start.optimisticReaction(Reaction.Lightning.emoji)
        assertEquals(0, result.countOf(Reaction.Lightning))
    }

    @Test
    fun visibleReactionsKeepTheSelectedReaction() {
        val result = article(reactions = mapOf(Reaction.Grinning.emoji to 2), mine = Reaction.Clapping.emoji)
        val visible = result.visibleReactions
        assertEquals(listOf(Reaction.Grinning, Reaction.Clapping), visible)
    }

    @Test
    fun everyReactionResolvesFromItsEmoji() {
        Reaction.entries.forEach { reaction ->
            assertEquals(reaction, Reaction.of(reaction.emoji))
        }
        assertNull(Reaction.of("🔥"))
    }

    @Test
    fun orderedCountsCoverEveryReaction() {
        val ordered = Reaction.ordered(mapOf(Reaction.Clapping.emoji to 5))
        assertEquals(Reaction.entries.size, ordered.size)
        assertEquals(5, ordered[Reaction.Clapping.emoji])
        assertEquals(0, ordered[Reaction.StarStruck.emoji])
    }
}
