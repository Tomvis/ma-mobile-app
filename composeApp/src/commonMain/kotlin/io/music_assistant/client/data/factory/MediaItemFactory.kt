package io.music_assistant.client.data.factory

import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.client.Chapter
import io.music_assistant.client.data.model.client.CriticalReception
import io.music_assistant.client.data.model.client.ImageInfo
import io.music_assistant.client.data.model.client.ImageType
import io.music_assistant.client.data.model.client.MediaType
import io.music_assistant.client.data.model.client.Metadata
import io.music_assistant.client.data.model.client.ReviewLink
import io.music_assistant.client.data.model.client.ReviewSource
import io.music_assistant.client.data.model.client.legacyAccolades
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Artist
import io.music_assistant.client.data.model.client.items.Audiobook
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.model.client.items.Playlist
import io.music_assistant.client.data.model.client.items.Podcast
import io.music_assistant.client.data.model.client.items.PodcastEpisode
import io.music_assistant.client.data.model.client.items.RadioStation
import io.music_assistant.client.data.model.client.items.RecommendationFolder
import io.music_assistant.client.data.model.client.items.SoundEffect
import io.music_assistant.client.data.model.client.items.Track
import io.music_assistant.client.data.model.server.SearchResult
import io.music_assistant.client.data.model.server.ServerCriticalReception
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.server.ServerMediaItemChapter
import io.music_assistant.client.data.model.server.ServerMediaItemImage
import io.music_assistant.client.data.model.server.ServerMetadata
import io.music_assistant.client.data.repository.SearchResultData

/**
 * Maps server-side [ServerMediaItem] DTOs into typed client [AppMediaItem] subtypes.
 *
 * Single concrete dispatcher — keep all type-switching here so subtypes stay dumb data classes.
 * Pure & stateless; safe to register as a Koin `single`.
 */
class MediaItemFactory(
    private val apiClient: ServiceClient,
) {
    fun create(server: ServerMediaItem): AppMediaItem? = with(server) {
        when (MediaType.fromServer(mediaType)) {
            MediaType.ARTIST -> Artist(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                favorite = favorite,
                sortName = sortName,
                uri = uri,
                images = resolveImageInfo(image, metadata),
            )

            MediaType.ALBUM -> Album(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                favorite = favorite,
                sortName = sortName,
                uri = uri,
                images = resolveImageInfo(image, metadata),
                version = version,
                year = year,
                artists = artists?.mapNotNull { create(it) as? Artist } ?: emptyList(),
                listenLater = listenLater == true,
            )

            MediaType.TRACK -> Track(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                favorite = favorite,
                sortName = sortName,
                uri = uri,
                images = resolveImageInfo(image, metadata),
                duration = duration,
                isPlayable = isPlayable == true,
                artists = artists?.mapNotNull { create(it) as? Artist } ?: emptyList(),
                album = album?.let { create(it) as? Album },
                discNumber = discNumber,
                trackNumber = trackNumber,
                position = position?.takeIf { it in 0L..Int.MAX_VALUE.toLong() }?.toInt(),
                version = version,
                source = server,
            )

            MediaType.PLAYLIST -> Playlist(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                favorite = favorite,
                sortName = sortName,
                uri = uri,
                images = resolveImageInfo(image, metadata),
                isEditable = isEditable == true,
                isDynamic = isDynamic == true,
            )

            MediaType.FOLDER -> RecommendationFolder(
                itemId = itemId,
                provider = provider,
                name = name,
                uri = uri,
                images = resolveImageInfo(image, metadata),
                items = items?.let { createList(it) },
                path = path,
                isPlayable = isPlayable == true,
            )

            MediaType.PODCAST -> Podcast(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                favorite = favorite,
                sortName = sortName,
                // The server's feed parser puts the feed's website link in `uri`; build the MA uri instead.
                uri = "$provider://${MediaType.PODCAST.serverValue}/$itemId",
                images = resolveImageInfo(image, metadata),
            )

            MediaType.PODCAST_EPISODE -> PodcastEpisode(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                favorite = favorite,
                sortName = sortName,
                uri = uri,
                images = resolveImageInfo(image, metadata),
                duration = duration,
                isPlayable = isPlayable == true,
                podcast = podcast?.let { create(it) as? Podcast },
                fullyPlayed = fullyPlayed,
                resumePositionMs = resumePositionMs,
                releaseDate = metadata?.releaseDate,
                version = version,
                source = server,
            )

            MediaType.RADIO -> RadioStation(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                favorite = favorite,
                sortName = sortName,
                uri = uri,
                images = resolveImageInfo(image, metadata),
                version = version,
                isPlayable = isPlayable == true,
                isDynamic = isDynamic == true,
            )

            MediaType.SOUND_EFFECT -> SoundEffect(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                sortName = sortName,
                images = resolveImageInfo(image, metadata),
                duration = duration,
            )

            MediaType.AUDIOBOOK -> Audiobook(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                favorite = favorite,
                sortName = sortName,
                uri = uri,
                images = resolveImageInfo(image, metadata),
                duration = duration,
                isPlayable = isPlayable == true,
                authors = authors,
                narrators = narrators,
                chapters = metadata?.chapters?.map(::createChapter),
                fullyPlayed = fullyPlayed,
                resumePositionMs = resumePositionMs,
                version = version,
                source = server,
            )

            MediaType.GENRE -> Genre(
                itemId = itemId,
                provider = provider,
                name = name,
                providerMappings = providerMappings,
                metadata = createMetadata(metadata),
                favorite = favorite,
                sortName = sortName,
                uri = uri,
                images = resolveImageInfo(image, metadata),
            )

            MediaType.FLOW_STREAM,
            MediaType.ANNOUNCEMENT,
            MediaType.AUDIO_SOURCE,
            MediaType.UNKNOWN,
            null,
                -> null
        }
    }

    fun createList(servers: List<ServerMediaItem>): List<AppMediaItem> =
        servers.mapNotNull { create(it) }

    fun createSearchResult(search: SearchResult): SearchResultData = SearchResultData(
        artists = search.artists.mapNotNull { create(it) as? Artist },
        albums = search.albums.mapNotNull { create(it) as? Album },
        tracks = search.tracks.mapNotNull { create(it) as? Track },
        playlists = search.playlists.mapNotNull { create(it) as? Playlist },
        audiobooks = search.audiobooks.mapNotNull { create(it) as? Audiobook },
        podcasts = search.podcasts.mapNotNull { create(it) as? Podcast },
        radios = search.radio.mapNotNull { create(it) as? RadioStation },
        genres = search.genres.mapNotNull { create(it) as? Genre },
    )

    private fun createMetadata(server: ServerMetadata?): Metadata? = server?.let {
        Metadata(
            explicit = it.explicit == true,
            images = it.images?.map(::createImageInfo).orEmpty(),
            releaseDate = it.releaseDate,
            chapters = it.chapters?.map(::createChapter).orEmpty(),
            lyrics = it.lyrics,
            lrcLyrics = it.lrcLyrics,
            dynamicRange = it.dynamicRange?.toFloat(),
            criticalReception = createCriticalReception(it.criticalReception),
        )
    }

    private fun createCriticalReception(server: ServerCriticalReception?): CriticalReception? {
        if (server == null) return null
        val sources = server.sources
            // Drop unusable entries: the server may serialize a missing source as
            // blank or the literal "None" (Python None -> "None").
            ?.filter { it.source.isNotBlank() && it.source != "None" }
            ?.map { entry ->
                // Prefer the 3.2.0/3.3.0 shape; fold pre-3.2.0 types/labels into accolades
                // and a pre-3.3.0 review_url into a single "Review" link during the transition.
                // takeIf isNotEmpty (not just non-null): a transitional payload may send
                // accolades: [] / links: [] alongside the legacy fields, and an empty list
                // must still fall through to the legacy fold (mirrors the server's falsy
                // `if not src.accolades` / `if not links` guards).
                val accolades = entry.accolades?.takeIf { it.isNotEmpty() }
                    ?: legacyAccolades(entry.types.orEmpty(), entry.labels.orEmpty())
                val links = entry.links
                    ?.filter { it.url.isNotBlank() }
                    ?.map { ReviewLink(it.label, it.url) }
                    ?.takeIf { it.isNotEmpty() }
                    ?: entry.reviewUrl?.takeIf { it.isNotBlank() }
                        ?.let { listOf(ReviewLink("Review", it)) }
                    ?: emptyList()
                ReviewSource(
                    source = entry.source,
                    rating = entry.rating?.toFloat(),
                    favorite = entry.favorite,
                    accolades = accolades,
                    links = links,
                    authors = entry.authors.orEmpty(),
                )
            }
            .orEmpty()
        if (server.amgDr == null && sources.isEmpty()) return null
        return CriticalReception(amgDr = server.amgDr?.toFloat(), sources = sources)
    }

    private fun createChapter(server: ServerMediaItemChapter): Chapter =
        Chapter(
            position = server.position,
            name = server.name,
            start = server.start,
            end = server.end,
        )

    private fun createImageInfo(server: ServerMediaItemImage): ImageInfo =
        ImageInfo(
            type = ImageType.fromServer(server.type),
            path = server.path,
            isRemotelyAccessible = server.remotelyAccessible,
            provider = server.provider,
            url = apiClient.resolveImageUrl(server.path, server.provider, server.remotelyAccessible, server.proxyId),
        )

    private fun resolveImageInfo(
        image: ServerMediaItemImage?,
        metadata: ServerMetadata?,
    ) = buildMap {
        image?.let { put(ImageType.MAIN, createImageInfo(it)) }
        metadata?.images?.map { createImageInfo(it) }
            ?.forEach { if (get(it.type) == null) put(it.type, it) }
    }
}
