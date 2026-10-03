@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.AlbumReview
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.DrInfo
import io.music_assistant.client.data.model.client.DrQuality
import io.music_assistant.client.data.model.client.DrSource
import io.music_assistant.client.data.model.client.ParsedAccolade
import io.music_assistant.client.data.model.client.ReceptionTags
import io.music_assistant.client.data.model.client.SourceTags
import io.music_assistant.client.data.model.client.albumReview
import io.music_assistant.client.data.model.client.formatDated
import io.music_assistant.client.data.model.client.formatDr
import io.music_assistant.client.data.model.client.formatScore
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.linksForAccolade
import io.music_assistant.client.data.model.client.reviewLink
import io.music_assistant.client.ui.compose.common.items.labelRes
import io.music_assistant.client.ui.compose.common.items.rememberAlbumReceptionTags
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.reception_amg_dr_fallback
import musicassistantclient.composeapp.generated.resources.reception_amg_dr_reported
import musicassistantclient.composeapp.generated.resources.reception_dynamic_range
import musicassistantclient.composeapp.generated.resources.reception_personal_pick
import musicassistantclient.composeapp.generated.resources.reception_score_with_max
import musicassistantclient.composeapp.generated.resources.reception_title
import org.jetbrains.compose.resources.stringResource

/** The album's critic data, then its review text; renders nothing when it has neither. */
@Composable
fun AlbumReceptionPanel(album: Album, modifier: Modifier = Modifier) {
    val tags = rememberAlbumReceptionTags(album)
    val review = remember(album.metadata) { albumReview(album.metadata) }
    if (!tags.hasAny && review == null) return
    ReceptionPanel(tags, review, modifier)
}

@Composable
private fun ReceptionPanel(tags: ReceptionTags, review: AlbumReview?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (tags.hasAny) {
                Text(
                    text = stringResource(Res.string.reception_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                tags.dr?.let { DrRow(it, tags.amgDr) }
                tags.amg?.let { SourceRow(it) }
                tags.tps?.let { SourceRow(it) }
            }
            review?.let {
                if (tags.hasAny) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AlbumReviewSection(it)
            }
        }
    }
}

@Composable
private fun drVerdict(quality: DrQuality): String = stringResource(quality.labelRes())

@Composable
private fun DrRow(dr: DrInfo, amgDr: DrInfo?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.reception_dynamic_range),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (dr.source == DrSource.AMG) {
                Text(
                    text = stringResource(Res.string.reception_amg_dr_fallback),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val verdict = drVerdict(dr.quality)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = formatDr(dr.value), style = MaterialTheme.typography.headlineSmall)
            LinearProgressIndicator(
                progress = { (dr.value / 20f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { stateDescription = "${formatDr(dr.value)} / 20 — $verdict" },
            )
            Text(text = verdict, style = MaterialTheme.typography.labelLarge)
        }
        amgDr?.let {
            Text(
                text = stringResource(Res.string.reception_amg_dr_reported, formatDr(it.value)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceRow(s: SourceTags) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = s.source,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(48.dp),
            )
            if (s.rating != null) {
                // The score links out to the canonical "Review" post when one exists.
                val reviewUrl = reviewLink(s)?.url
                val scoreModifier = if (reviewUrl != null) {
                    Modifier.clickable { uriHandler.openUri(reviewUrl) }
                } else {
                    Modifier
                }
                Text(
                    text = stringResource(Res.string.reception_score_with_max, formatScore(s.rating), s.scale),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (reviewUrl != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = scoreModifier,
                )
                LinearProgressIndicator(
                    progress = { (s.rating / s.scale).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { stateDescription = "${formatScore(s.rating)} / ${s.scale}" },
                )
            } else if (s.favorite) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(Res.string.reception_personal_pick), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (s.accolades.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.accolades.forEach { AccoladeChip(it, s, uriHandler) }
            }
        }
        if (s.authors.isNotEmpty()) {
            Text(
                text = "— " + s.authors.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun accoladeText(accolade: ParsedAccolade): String {
    val res = accolade.kind.labelRes() ?: return accolade.display
    // Re-attach the parsed date facet to the localized name (mirrors the web's
    // accoladeDisplay) from the structured year/month the model already carries.
    return formatDated(stringResource(res), accolade.year, accolade.month)
}

@Composable
private fun AccoladeChip(accolade: ParsedAccolade, source: SourceTags, uriHandler: UriHandler) {
    // One accolade can map to several posts; open the first (the chip is non-interactive
    // when no post link is attached, matching the pre-3.3.0 behavior).
    val url = linksForAccolade(source, accolade).firstOrNull()?.url
    AssistChip(
        onClick = { url?.let { uriHandler.openUri(it) } },
        enabled = url != null,
        label = { Text(accoladeText(accolade)) },
        leadingIcon = if (accolade.isAward) {
            {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = AssistChipDefaults.assistChipColors().labelColor,
                )
            }
        } else {
            null
        },
        // Visible affordance that this chip opens its source post.
        trailingIcon = if (url != null) {
            {
                Icon(
                    Icons.Default.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            null
        },
    )
}

@Preview
@Composable
private fun ReceptionPanelPreview() {
    AlbumReceptionPanel(AppMediaItemFixtures.album(metadata = AppMediaItemFixtures.receptionMetadata()))
}

@Preview
@Composable
private fun ReceptionPanelEmptyPreview() {
    AlbumReceptionPanel(AppMediaItemFixtures.album())
}

@Preview
@Composable
private fun ReceptionPanelFallbackPreview() {
    AlbumReceptionPanel(AppMediaItemFixtures.album(metadata = AppMediaItemFixtures.receptionMetadataFallback()))
}

@Preview
@Composable
private fun ReceptionPanelDescriptionPreview() {
    AlbumReceptionPanel(
        AppMediaItemFixtures.album(
            metadata = AppMediaItemFixtures.receptionMetadataFallback().copy(
                criticalReception = null,
                description = "The sixth and final studio album by the band, released in 2011.",
            ),
        ),
    )
}
