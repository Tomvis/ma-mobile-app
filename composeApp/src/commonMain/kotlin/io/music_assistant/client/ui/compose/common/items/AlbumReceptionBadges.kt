@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.DrInfo
import io.music_assistant.client.data.model.client.DrQuality
import io.music_assistant.client.data.model.client.SourceTags
import io.music_assistant.client.data.model.client.formatDr
import io.music_assistant.client.data.model.client.formatScore
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.parseAlbumReception

enum class ReceptionBadgeStyle { Tile, Row }

/**
 * Compact DR / AMG / TPS reception chips for album tiles and rows. Renders nothing
 * when the album has no reception data. DR is tinted by quality band (scannability);
 * AMG/TPS are neutral tonal. Mirrors the web AlbumPanelBadges / AlbumListBadges.
 */
@Composable
fun AlbumReceptionBadges(
    album: Album,
    style: ReceptionBadgeStyle,
    modifier: Modifier = Modifier,
) {
    val tags = remember(album.metadata) {
        parseAlbumReception(
            album.metadata?.criticalReception,
            album.metadata?.dynamicRange,
        )
    }
    if (!tags.hasAny) return

    val contentDesc = buildList {
        tags.dr?.let { add("DR ${formatDr(it.value)}") }
        tags.amg?.let { s -> add(s.source + (s.rating?.let { " ${formatScore(it)}" } ?: "")) }
        tags.tps?.let { s -> add(s.source + (s.rating?.let { " ${formatScore(it)}" } ?: "")) }
    }.joinToString(", ")

    val container = if (style == ReceptionBadgeStyle.Tile) {
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 3.dp, vertical = 2.dp)
    } else {
        modifier
    }

    Row(
        modifier = container.semantics(mergeDescendants = true) {
            contentDescription = contentDesc
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tags.dr?.let { DrPill(it) }
        tags.amg?.let { SourcePill(it) }
        tags.tps?.let { SourcePill(it) }
    }
}

// Restrained semantic hues for the DR pill, matching the web DR palette. Used as a
// low-alpha container with the same hue as the (more saturated) text, so it reads on
// both light and dark themes and over the tile scrim.
private fun drHue(quality: DrQuality): Color = when (quality) {
    DrQuality.EXCELLENT -> Color(0xFF22C55E)
    DrQuality.GOOD -> Color(0xFF3B82F6)
    DrQuality.FAIR -> Color(0xFFEAB308)
    DrQuality.POOR -> Color(0xFFEF4444)
}

@Composable
private fun DrPill(dr: DrInfo) {
    val hue = drHue(dr.quality)
    Pill(
        text = "DR ${formatDr(dr.value)}",
        container = hue.copy(alpha = 0.22f),
        content = hue,
    )
}

@Composable
private fun SourcePill(s: SourceTags) {
    // AMG always shows the star; TPS shows the star only when there is no rating.
    val showStar = s.source == "AMG" || s.rating == null
    Pill(
        text = s.rating?.let { formatScore(it) },
        container = MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
        leadingStar = showStar,
    )
}

@Composable
private fun Pill(
    text: String?,
    container: Color,
    content: Color,
    leadingStar: Boolean = false,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingStar) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(11.dp),
            )
        }
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = content,
            )
        }
    }
}

@Preview
@Composable
private fun BadgesRowPreview() {
    AlbumReceptionBadges(
        AppMediaItemFixtures.album(metadata = AppMediaItemFixtures.receptionMetadata()),
        style = ReceptionBadgeStyle.Row,
    )
}

@Preview
@Composable
private fun BadgesTilePreview() {
    Box {
        AlbumReceptionBadges(
            AppMediaItemFixtures.album(metadata = AppMediaItemFixtures.receptionMetadata()),
            style = ReceptionBadgeStyle.Tile,
        )
    }
}

@Preview
@Composable
private fun BadgesEmptyPreview() {
    // No metadata -> renders nothing.
    AlbumReceptionBadges(AppMediaItemFixtures.album(), style = ReceptionBadgeStyle.Row)
}

@Preview
@Composable
private fun BadgesFallbackPreview() {
    // AMG-reported DR fallback + a favorite-only source (no rating) -> star-only pill.
    AlbumReceptionBadges(
        AppMediaItemFixtures.album(metadata = AppMediaItemFixtures.receptionMetadataFallback()),
        style = ReceptionBadgeStyle.Row,
    )
}
