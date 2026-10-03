package io.music_assistant.client.ui.compose.item

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.AlbumReview
import io.music_assistant.client.data.model.client.AlbumReviewKind
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.review_about_title
import musicassistantclient.composeapp.generated.resources.review_show_less
import musicassistantclient.composeapp.generated.resources.review_show_more
import musicassistantclient.composeapp.generated.resources.review_title
import org.jetbrains.compose.resources.stringResource

private const val COLLAPSED_LINES = 6

/**
 * The album's review text under the critic data: collapsed to a few lines with tap-to-expand,
 * and, for a critic review, signed "— authors, site" with the site opening the review post.
 */
@Composable
fun AlbumReviewSection(review: AlbumReview, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(review.text) { mutableStateOf(false) }
    var overflows by remember(review.text) { mutableStateOf(false) }
    val toggleable = overflows || expanded
    val toggleLabel = stringResource(if (expanded) Res.string.review_show_less else Res.string.review_show_more)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(
                if (review.kind == AlbumReviewKind.DESCRIPTION) Res.string.review_about_title else Res.string.review_title,
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .then(
                    if (toggleable) {
                        Modifier.clickable(onClickLabel = toggleLabel) { expanded = !expanded }
                    } else {
                        Modifier
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = review.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
            )
            if (toggleable) {
                Text(
                    text = toggleLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        review.site?.let { ReviewSignature(review.authors, it, review.url) }
    }
}

@Composable
private fun ReviewSignature(authors: List<String>, site: String, url: String?) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val signature = buildAnnotatedString {
        append("— ")
        authors.forEach { append("$it, ") }
        if (url != null) {
            withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { append(site) }
        } else {
            append(site)
        }
    }
    Row(
        modifier = url?.let { Modifier.clickable { uriHandler.openUri(it) } } ?: Modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = signature,
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (url != null) {
            Icon(
                Icons.Default.OpenInNew,
                contentDescription = null,
                tint = linkColor,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
