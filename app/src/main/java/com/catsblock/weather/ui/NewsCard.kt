package com.catsblock.weather.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.catsblock.weather.data.NewsArticle
import com.catsblock.weather.data.NewsFeed
import com.catsblock.weather.data.Reaction
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.typography.GoogleSansFlex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NewsCard(
    article: NewsArticle,
    locale: java.util.Locale,
    onToggleLike: () -> Unit,
    onReact: (Reaction) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = scheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            article.source.ifBlank { tr("Source") },
                            color = scheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        val date = publishedLabel(article.publishedAt, locale)
                        if (date.isNotEmpty()) {
                            Text(" · ", color = scheme.onSurfaceVariant, fontSize = 11.sp)
                            Text(date, color = scheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        article.title,
                        color = scheme.onSurface,
                        fontFamily = GoogleSansFlex.family,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        lineHeight = 23.sp,
                        letterSpacing = (-0.2).sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (article.summary.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            newsDescription(article.summary),
                            color = scheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                val image = article.imageUrl
                if (!image.isNullOrBlank()) {
                    Spacer(Modifier.width(12.dp))
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(scheme.surfaceContainerHighest)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LikeButton(
                    liked = article.liked,
                    count = article.likes,
                    onToggle = onToggleLike
                )
                val visible = article.visibleReactions
                if (visible.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    ReactionStrip(article = article, reactions = visible, cardColor = scheme.surfaceContainer, onReact = onReact)
                    Spacer(Modifier.width(6.dp))
                } else {
                    Spacer(Modifier.width(6.dp))
                }
                ReactionPicker(article = article, onReact = onReact)
            }
        }
    }
}

@Composable
private fun RowScope.ReactionStrip(
    article: NewsArticle,
    reactions: List<Reaction>,
    cardColor: Color,
    onReact: (Reaction) -> Unit
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .weight(1f, fill = false)
            .horizontalScroll(scroll)
            .drawWithContent {
                drawContent()
                if (scroll.canScrollForward) {
                    val fade = 28.dp.toPx()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, cardColor),
                            startX = size.width - fade,
                            endX = size.width
                        ),
                        size = androidx.compose.ui.geometry.Size(fade, size.height),
                        topLeft = androidx.compose.ui.geometry.Offset(size.width - fade, 0f)
                    )
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        reactions.forEach { reaction ->
            ReactionChip(
                reaction = reaction,
                count = article.countOf(reaction),
                selected = article.myReaction == reaction.emoji,
                onClick = { onReact(reaction) }
            )
        }
    }
}

@Composable
private fun LikeButton(liked: Boolean, count: Int, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = rememberMotionEnabled()
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "likePress"
    )
    val fill by animateFloatAsState(
        if (liked) 1f else 0f,
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "likeFill"
    )
    val container by animateFloatAsState(if (liked) 1f else 0f, tween(220), label = "likeContainer")
    val active = scheme.primary
    val idle = scheme.onSurfaceVariant
    Surface(
        shape = CircleShape,
        color = lerp(Color.Transparent, active.copy(alpha = 0.14f), container),
        modifier = Modifier.clip(CircleShape)
    ) {
        Row(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale.value * press
                    scaleY = scale.value * press
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = {
                        onToggle()
                        if (!motion) return@clickable
                        scope.launch {
                            scale.snapTo(0.88f)
                            scale.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessLow))
                        }
                        if (!liked) {
                            scope.launch {
                                burst.snapTo(0f)
                                burst.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
                                burst.snapTo(0f)
                            }
                        }
                    }
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (burst.value > 0f) {
                    Canvas(Modifier.size(30.dp)) {
                        val progress = burst.value
                        drawCircle(
                            color = active.copy(alpha = (1f - progress) * 0.35f),
                            radius = size.minDimension * (0.22f + progress * 0.28f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx())
                        )
                    }
                }
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = tr(if (liked) "Unlike" else "Like"),
                    tint = active,
                    modifier = Modifier.size(18.dp).graphicsLayer { alpha = fill }
                )
                Icon(
                    Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = lerp(idle, active, fill),
                    modifier = Modifier.size(18.dp).graphicsLayer { alpha = 1f - fill * 0.65f }
                )
            }
            Spacer(Modifier.width(6.dp))
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn(tween(180))) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut(tween(120)))
                },
                label = "likeCount"
            ) { value ->
                Text(
                    if (value > 0) "${value.coerceAtMost(NewsFeed.MAX_LIKES)}" else tr("Like"),
                    color = lerp(idle, active, fill),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ReactionChip(reaction: Reaction, count: Int, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val selection by animateFloatAsState(if (selected) 1f else 0f, tween(220), label = "chipSelection")
    val scale by animateFloatAsState(if (selected) 1.04f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "chipScale")
    Surface(
        shape = CircleShape,
        color = lerp(scheme.surfaceContainerHigh, scheme.primary.copy(alpha = 0.16f), selection),
        border = if (selected) BorderStroke(1.dp, scheme.primary.copy(alpha = 0.45f)) else null,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Twemoji(
                reaction = reaction,
                size = 16.dp,
                contentDescription = "${tr("Reaction")}: ${tr(reaction.label)}"
            )
            Spacer(Modifier.width(4.dp))
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn(tween(160))) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut(tween(110)))
                },
                label = "reactionCount"
            ) { value ->
                Text(
                    "$value",
                    color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ReactionPicker(article: NewsArticle, onReact: (Reaction) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = rememberMotionEnabled()
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            shape = CircleShape,
            color = scheme.surfaceContainerHigh,
            modifier = Modifier.clip(CircleShape).clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = tr("Add reaction"),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(0.dp, 6.dp),
            shape = RoundedCornerShape(26.dp),
            containerColor = scheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            ReactionMenuContent(
                article = article,
                motion = motion,
                onReact = { reaction ->
                    expanded = false
                    onReact(reaction)
                }
            )
        }
    }
}

@Composable
internal fun ReactionMenuContent(
    article: NewsArticle,
    onReact: (Reaction) -> Unit,
    motion: Boolean = rememberMotionEnabled()
) {
    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Reaction.entries.forEachIndexed { index, reaction ->
            ReactionOption(
                reaction = reaction,
                count = article.countOf(reaction),
                selected = article.myReaction == reaction.emoji,
                motion = motion,
                index = index,
                onClick = { onReact(reaction) }
            )
        }
    }
}

@Composable
private fun ReactionOption(
    reaction: Reaction,
    count: Int,
    selected: Boolean,
    motion: Boolean,
    index: Int,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val scale = remember { Animatable(if (motion) 0.72f else 1f) }
    LaunchedEffect(Unit) {
        if (!motion) return@LaunchedEffect
        delay(index * 26L)
        scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    val selection by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "optionSelection")
    Column(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .width(40.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(lerp(Color.Transparent, scheme.primary.copy(alpha = 0.16f), selection)),
            contentAlignment = Alignment.Center
        ) {
            Twemoji(
                reaction = reaction,
                size = 22.dp,
                contentDescription = tr(reaction.label)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "$count",
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

internal fun reactionSummary(counts: Map<String, Int>): Int = counts.values.sum()
