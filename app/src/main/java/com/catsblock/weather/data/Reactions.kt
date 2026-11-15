package com.catsblock.weather.data

import androidx.annotation.DrawableRes
import com.catsblock.weather.R

enum class Reaction(val emoji: String, val label: String, @DrawableRes val drawable: Int) {
    StarStruck("🤩", "Star-struck", R.drawable.twemoji_1f929),
    Grinning("😁", "Grinning", R.drawable.twemoji_1f601),
    Crying("😭", "Crying", R.drawable.twemoji_1f62d),
    Lightning("⚡", "Lightning", R.drawable.twemoji_26a1),
    ThumbsUp("👍", "Thumbs up", R.drawable.twemoji_1f44d),
    Clapping("👏", "Clapping", R.drawable.twemoji_1f44f);

    companion object {
        fun of(emoji: String?): Reaction? = entries.firstOrNull { it.emoji == emoji }
    }
}
