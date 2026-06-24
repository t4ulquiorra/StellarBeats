package com.stellarbeats.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

internal object InnertubeParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parseHome(raw: JsonElement): HomeResult {
        val contents = raw
            .walkPath("contents", "singleColumnBrowseResultsRenderer", "tabs")
            ?.jsonArray?.firstOrNull()
            ?.walkPath("tabRenderer", "content", "sectionListRenderer", "contents")
            ?.jsonArray ?: return HomeResult(emptyList(), null)
        val sections = contents.mapNotNull { sectionEl ->
            val section = sectionEl.jsonObject
                .get("itemSectionRenderer")?.jsonObject
                ?.get("contents")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("musicCarouselShelfRenderer")?.jsonObject
                ?: return@mapNotNull null
            val title = section.getText("header", "musicCarouselShelfBasicHeaderRenderer", "title") ?: return@mapNotNull null
            val items = section.get("contents")?.jsonArray
                ?.mapNotNull { it.jsonObject?.let { obj -> parseSearchItem(obj) } }
                ?: emptyList()
            val continuation = section.walkPath("continuations")?.jsonArray?.firstOrNull()
                ?.walkPath("nextContinuationData", "continuation")?.jsonPrimitive?.content
            HomeSection(title, items, null, continuation)
        }
        val mainContinuation = raw.walkPath("continuations")?.jsonArray?.firstOrNull()
            ?.walkPath("nextContinuationData", "continuation")?.jsonPrimitive?.content
        return HomeResult(sections, mainContinuation)
    }

    fun parseSearch(raw: JsonElement): SearchResult {
        val contents = raw.walkPath("contents", "sectionListRenderer", "contents")
            ?.jsonArray ?: return SearchResult(emptyList(), null)
        val items = contents.flatMap { sectionEl ->
            sectionEl.jsonObject?.get("itemSectionRenderer")?.jsonObject
                ?.get("contents")?.jsonArray
                ?.mapNotNull { it.jsonObject?.let { parseSearchItem(it) } }
                ?: emptyList()
        }
        val continuation = raw.walkPath("continuations")?.jsonArray?.firstOrNull()
            ?.walkPath("nextContinuationData", "continuation")?.jsonPrimitive?.content
        return SearchResult(items, continuation)
    }

    fun parsePlayer(raw: JsonElement): PlayerResponse {
        val playability = raw.walkPath("playabilityStatus")?.jsonObject
            ?: throw PlayabilityException("UNKNOWN", "Missing playability status")
        val status = playability["status"]?.jsonPrimitive?.content ?: "UNKNOWN"
        if (status != "OK") {
            val reason = playability.getText("reason") ?: "Unknown error"
            throw PlayabilityException(status, reason)
        }
        val videoDetails = raw.walkPath("videoDetails")?.jsonObject
            ?: throw InnertubeException("Missing videoDetails")
        val videoId = videoDetails["videoId"]?.jsonPrimitive?.content
            ?: throw InnertubeException("Missing videoId")
        val streamingData = raw.walkPath("streamingData")?.jsonObject
        val streams = extractAudioStreams(streamingData)
        if (streams.isEmpty()) {
            throw StreamException("No audio streams found for $videoId")
        }
        val thumbnail = videoDetails.walkPath("thumbnail", "thumbnails")
            ?.jsonArray?.maxByOrNull { it.jsonObject?.get("width")?.jsonPrimitive?.intOrNull ?: 0 }
            ?.jsonObject?.let {
                Thumbnail(
                    url = it["url"]?.jsonPrimitive?.content ?: "",
                    width = it["width"]?.jsonPrimitive?.intOrNull ?: 0,
                    height = it["height"]?.jsonPrimitive?.intOrNull ?: 0,
                )
            }
        val lyricsEndpoint = raw.walkPath("captions", "playerCaptionsTracklistRenderer", "captionTracks")
            ?.jsonArray?.firstOrNull()?.jsonObject?.let { cap ->
                NavigationEndpoint(type = ContentType.UNKNOWN, watchEndpointTimedTextTrackId = cap["baseUrl"]?.jsonPrimitive?.content)
            }
        return PlayerResponse(
            videoId = videoId,
            title = videoDetails.getText("title") ?: "Unknown",
            artist = videoDetails.getText("author") ?: "Unknown",
            artistEndpoint = videoDetails.walkPath("channelId")
                ?.jsonPrimitive?.content?.let { NavigationEndpoint(ContentType.ARTIST, browseId = it) },
            album = null, albumEndpoint = null, thumbnail = thumbnail,
            durationSeconds = videoDetails["lengthSeconds"]?.jsonPrimitive?.intOrNull ?: 0,
            streams = streams, lyricsEndpoint = lyricsEndpoint, relatedEndpoint = null,
        )
    }

    fun parseBrowse(raw: JsonElement): BrowseResult {
        val tabs = raw.walkPath("contents", "twoColumnBrowseResultsRenderer", "tabs")
            ?.jsonArray ?: raw.walkPath("contents", "singleColumnBrowseResultsRenderer", "tabs")
            ?.jsonArray ?: return BrowseResult(null, emptyList(), null)
        val tabContent = tabs.firstNotNullOfOrNull { tab -> tab.walkPath("tabRenderer", "content") }
            ?: return BrowseResult(null, emptyList(), null)
        val header = tabContent.walkPath("sectionListRenderer", "header")?.let { parseSectionHeader(it) }
        val sectionContents = tabContent.walkPath("sectionListRenderer", "contents")
            ?.jsonArray ?: tabContent.walkPath("playlistVideoListRenderer", "contents")?.jsonArray
        val items = sectionContents?.mapNotNull { el ->
            el.jsonObject?.let { obj -> parseBrowseItem(obj) ?: parseSearchItem(obj)?.toBrowseItem() }
        } ?: emptyList()
        val continuation = raw.walkPath("continuations")
            ?.jsonArray?.firstOrNull()?.walkPath("nextContinuationData", "continuation")?.jsonPrimitive?.content
            ?: tabContent.walkPath("playlistVideoListRenderer", "continuations")?.jsonArray
                ?.firstOrNull()?.walkPath("nextContinuationData", "continuation")?.jsonPrimitive?.content
        return BrowseResult(header, items, continuation)
    }

    fun parseNext(raw: JsonElement): NextResult {
        val tabContent = raw
            .walkPath("contents", "singleColumnMusicWatchNextResultsRenderer", "tabbedRenderer", "tabs")
            ?.jsonArray?.firstOrNull()?.walkPath("tabRenderer", "content")
        val queueItems = tabContent
            ?.walkPath("musicQueueRenderer", "content", "playlistPanelRenderer", "contents")
            ?.jsonArray ?: emptyList()
        val items = queueItems.mapNotNull { el ->
            el.jsonObject?.get("playlistPanelVideoRenderer")?.jsonObject?.let { parseQueueSong(it) }
        }
        val autoplay = raw
            .walkPath("contents", "singleColumnMusicWatchNextResultsRenderer", "autoplay", "autoplayRenderer")
            ?.walkPath("contents")?.jsonArray?.firstOrNull()
            ?.walkPath("autoplayVideoRenderer", "videoId")?.jsonPrimitive?.content
        val continuation = tabContent
            ?.walkPath("musicQueueRenderer", "content", "playlistPanelRenderer", "continuations")?.jsonArray
            ?.firstOrNull()?.walkPath("nextContinuationData", "continuation")?.jsonPrimitive?.content
        val lyricsEndpoint = raw
            .walkPath("contents", "singleColumnMusicWatchNextResultsRenderer", "tabbedRenderer", "tabs")
            ?.jsonArray?.getOrNull(1)?.walkPath("tabRenderer", "endpoint")
            ?.let { parseNavigationEndpoint(it) }
        return NextResult(items, autoplay, continuation, lyricsEndpoint)
    }

    fun parseMoodPlaylists(raw: JsonElement): List<MoodPlaylist> {
        val categories = raw
            .walkPath("contents", "singleColumnBrowseResultsRenderer", "tabs")
            ?.jsonArray?.firstOrNull()?.walkPath("tabRenderer", "content", "sectionListRenderer", "contents")
            ?.jsonArray ?: return emptyList()
        return categories.flatMap { sectionEl ->
            sectionEl.jsonObject?.get("itemSectionRenderer")?.jsonObject
                ?.get("contents")?.jsonArray ?: emptyList()
        }.mapNotNull { gridEl ->
            gridEl.jsonObject?.get("gridRenderer")?.jsonObject?.get("items")?.jsonArray
        }.flatten().mapNotNull { itemEl ->
            itemEl.jsonObject?.get("musicNavigationButtonRenderer")?.jsonObject?.let { renderer ->
                val endpoint = renderer.get("clickCommand")?.let { parseNavigationEndpoint(it) }
                    ?: renderer.get("navigationEndpoint")?.let { parseNavigationEndpoint(it) }
                MoodPlaylist(
                    title = renderer.getText("buttonText") ?: return@let null,
                    thumbnail = renderer.extractThumbnail(),
                    endpoint = endpoint,
                    params = renderer.walkPath("clickCommand", "browseEndpoint", "params")?.jsonPrimitive?.content,
                )
            }
        }
    }

    private fun extractAudioStreams(streamingData: JsonObject?): List<AudioStream> {
        val formats = streamingData?.get("adaptiveFormats")?.jsonArray ?: return emptyList()
        return formats.mapNotNull { formatEl ->
            val format = formatEl.jsonObject ?: return@mapNotNull null
            val mimeType = format["mimeType"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (!mimeType.startsWith("audio/")) return@mapNotNull null
            val rawUrl = resolveStreamUrl(format) ?: return@mapNotNull null
            AudioStream(
                itag = format["itag"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null,
                mimeType = mimeType,
                bitrate = format["bitrate"]?.jsonPrimitive?.intOrNull ?: 0,
                sampleRate = format["audioSampleRate"]?.jsonPrimitive?.intOrNull ?: 44100,
                channels = format["audioChannels"]?.jsonPrimitive?.intOrNull ?: 2,
                contentLength = format["contentLength"]?.jsonPrimitive?.content?.toLongOrNull(),
                url = rawUrl,
            )
        }.sortedByDescending { it.bitrate }
    }

    private fun resolveStreamUrl(format: JsonObject): String? {
        format["url"]?.jsonPrimitive?.content?.let { return transformNParam(it) }
        val cipher = format["signatureCipher"]?.jsonPrimitive?.content ?: return null
        return decryptSignatureCipher(cipher)
    }

    private fun decryptSignatureCipher(cipher: String): String? {
        val params = cipher.split("&").associate { pair ->
            val idx = pair.indexOf('=')
            if (idx == -1) return null
            pair.substring(0, idx) to java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
        }
        val encryptedSig = params["s"] ?: return null
        val sigKey = params["sp"] ?: "sig"
        val url = params["url"] ?: return null
        val decryptedSig = decryptSignature(encryptedSig) ?: return null
        return transformNParam("$url&$sigKey=$decryptedSig")
    }

    private fun decryptSignature(encrypted: String): String? = null
    private fun transformNParam(url: String): String = url

    private fun parseSearchItem(obj: JsonObject): SearchItem? {
        obj["musicTwoRowItemRenderer"]?.jsonObject?.let { return parseTwoRowItem(it) }
        obj["musicResponsiveListItemRenderer"]?.jsonObject?.let { return parseListItem(it) }
        obj["musicCardShelfRenderer"]?.jsonObject?.let { return parseCardItem(it) }
        return null
    }

    private fun parseTwoRowItem(r: JsonObject): SearchItem? {
        val title = r.getText("title") ?: return null
        val subtitle = r.getText("subtitle")
        val thumbnail = r.extractThumbnail()
        val endpoint = r.get("navigationEndpoint")?.let { parseNavigationEndpoint(it) }
        return when (endpoint?.type) {
            ContentType.SONG, ContentType.VIDEO -> SearchItem.Song(
                id = endpoint.videoId ?: "", title = title, thumbnail = thumbnail,
                endpoint = endpoint, subtitle = subtitle, artists = emptyList(),
                album = null, duration = parseDuration(subtitle),
            )
            ContentType.ALBUM -> SearchItem.Album(
                id = endpoint.browseId ?: "", title = title, thumbnail = thumbnail,
                endpoint = endpoint, subtitle = subtitle, year = parseYear(subtitle),
                songCount = parseSongCount(subtitle), artists = emptyList(),
            )
            ContentType.ARTIST -> SearchItem.Artist(
                id = endpoint.browseId ?: "", title = title, thumbnail = thumbnail,
                endpoint = endpoint, subtitle = subtitle,
            )
            ContentType.PLAYLIST -> SearchItem.Playlist(
                id = endpoint.playlistId ?: "", title = title, thumbnail = thumbnail,
                endpoint = endpoint, subtitle = subtitle, songCount = parseSongCount(subtitle),
                author = subtitle?.split(" • ")?.firstOrNull(),
            )
            else -> SearchItem.Unknown(title = title, thumbnail = thumbnail, endpoint = endpoint, subtitle = subtitle)
        }
    }

    private fun parseListItem(r: JsonObject): SearchItem? {
        val title = r.getFlexColumnItem(0)?.getText("text") ?: return null
        val subtitle = r.getFlexColumnItem(1)?.getText("text")
        val thumbnail = r.extractThumbnail()
        val endpoint = (r.get("playNavigationEndpoint") ?: r.get("navigationEndpoint"))
            ?.let { parseNavigationEndpoint(it) }
        return when (endpoint?.type) {
            ContentType.SONG, ContentType.VIDEO -> SearchItem.Song(
                id = endpoint.videoId ?: "", title = title, thumbnail = thumbnail,
                endpoint = endpoint, subtitle = subtitle, artists = emptyList(),
                album = null, duration = parseDuration(subtitle),
            )
            ContentType.ALBUM -> SearchItem.Album(
                id = endpoint.browseId ?: "", title = title, thumbnail = thumbnail,
                endpoint = endpoint, subtitle = subtitle, year = parseYear(subtitle),
                songCount = parseSongCount(subtitle), artists = emptyList(),
            )
            else -> SearchItem.Unknown(title = title, thumbnail = thumbnail, endpoint = endpoint, subtitle = subtitle)
        }
    }

    private fun parseCardItem(r: JsonObject): SearchItem? {
        val title = r.getText("title") ?: return null
        val subtitle = r.getText("subtitle")
        val thumbnail = r.extractThumbnail()
        val endpoint = r.get("title")?.jsonObject?.get("runs")?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("navigationEndpoint")
            ?.let { parseNavigationEndpoint(it) }
        return when (endpoint?.type) {
            ContentType.ALBUM -> SearchItem.Album(
                id = endpoint.browseId ?: "", title = title, thumbnail = thumbnail,
                endpoint = endpoint, subtitle = subtitle, year = parseYear(subtitle),
                songCount = parseSongCount(subtitle), artists = emptyList(),
            )
            ContentType.PLAYLIST -> SearchItem.Playlist(
                id = endpoint.playlistId ?: "", title = title, thumbnail = thumbnail,
                endpoint = endpoint, subtitle = subtitle, songCount = parseSongCount(subtitle),
                author = subtitle?.split(" • ")?.firstOrNull(),
            )
            else -> SearchItem.Unknown(title = title, thumbnail = thumbnail, endpoint = endpoint, subtitle = subtitle)
        }
    }

    private fun parseBrowseItem(obj: JsonObject): BrowseItem? {
        obj["playlistVideoRenderer"]?.jsonObject?.let { renderer ->
            val videoId = renderer["videoId"]?.jsonPrimitive?.content ?: return null
            val title = renderer.getText("title") ?: return null
            val subtitle = renderer.getText("longBylineText") ?: renderer.getText("shortBylineText")
            val thumbnail = renderer.extractThumbnail()
            val endpoint = renderer.get("navigationEndpoint")?.let { parseNavigationEndpoint(it) }
            val durationStr = renderer.get("lengthSeconds")?.jsonPrimitive?.content
                ?: renderer.getText("lengthText")
            return BrowseItem.Song(
                id = videoId, title = title, thumbnail = thumbnail, endpoint = endpoint,
                subtitle = subtitle, artists = emptyList(), album = null,
                duration = durationStr?.toIntOrNull() ?: parseDuration(durationStr), videoId = videoId,
            )
        }
        obj["musicTwoRowItemRenderer"]?.jsonObject?.let { return parseTwoRowItem(it)?.toBrowseItem() }
        return null
    }

    private fun parseQueueSong(r: JsonObject): BrowseItem.Song? {
        val videoId = r["videoId"]?.jsonPrimitive?.content ?: return null
        val title = r.getText("title") ?: return null
        val thumbnail = r.extractThumbnail()
        val endpoint = r.get("navigationEndpoint")?.let { parseNavigationEndpoint(it) }
        val lengthText = r.getText("lengthText")
        val subtitleRuns = r.get("longBylineText")?.jsonObject?.get("runs")?.jsonArray
        return BrowseItem.Song(
            id = videoId, title = title, thumbnail = thumbnail, endpoint = endpoint,
            subtitle = subtitleRuns?.joinToString("") { it.jsonObject?.get("text")?.jsonPrimitive?.content ?: "" },
            artists = emptyList(), album = null, duration = parseDuration(lengthText), videoId = videoId,
        )
    }

    private fun parseNavigationEndpoint(el: JsonElement): NavigationEndpoint? {
        val obj = el.jsonObject ?: return null
        obj["watchEndpoint"]?.jsonObject?.let { watch ->
            return NavigationEndpoint(
                type = if (watch["watchEndpointMusicSupportedConfigs"] != null) ContentType.SONG else ContentType.VIDEO,
                videoId = watch["videoId"]?.jsonPrimitive?.content,
                playlistId = watch["playlistId"]?.jsonPrimitive?.content,
                params = watch["params"]?.jsonPrimitive?.content,
            )
        }
        obj["browseEndpoint"]?.jsonObject?.let { browse ->
            val browseId = browse["browseId"]?.jsonPrimitive?.content ?: return null
            val type = when {
                browseId.startsWith("MPRE") -> ContentType.ALBUM
                browseId.startsWith("UC") -> ContentType.ARTIST
                browseId.startsWith("VL") || browseId.startsWith("OL") ||
                    browseId.startsWith("PL") || browseId.startsWith("RD") ||
                    browseId.startsWith("ML") -> ContentType.PLAYLIST
                browseId.startsWith("FEmusic") -> ContentType.PLAYLIST
                else -> ContentType.UNKNOWN
            }
            return NavigationEndpoint(type = type, browseId = browseId,
                params = browse["params"]?.jsonPrimitive?.content)
        }
        return null
    }

    private fun parseSectionHeader(el: JsonElement): String? {
        return el.jsonObject?.get("sectionListHeaderRenderer")?.jsonObject?.getText("title")
    }

    private fun JsonObject.getText(vararg path: String): String? {
        var current: JsonElement = this
        for (key in path) {
            current = current.jsonObject?.get(key) ?: return null
        }
        current.jsonObject?.get("simpleText")?.jsonPrimitive?.content?.let { return it }
        return current.jsonObject?.get("runs")?.jsonArray
            ?.mapNotNull { it.jsonObject?.get("text")?.jsonPrimitive?.content }
            ?.joinToString("")
    }

    private fun JsonObject.getFlexColumnItem(index: Int): JsonObject? {
        return this["flexColumns"]?.jsonArray?.getOrNull(index)?.jsonObject
            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
    }

    private fun JsonObject.extractThumbnail(): Thumbnail? {
        val thumbnails = this["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray
            ?: this["musicThumbnailRenderer"]?.jsonObject?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
            ?: return null
        return thumbnails.mapNotNull { t ->
            t.jsonObject?.let {
                Thumbnail(
                    url = it["url"]?.jsonPrimitive?.content ?: return@let null,
                    width = it["width"]?.jsonPrimitive?.intOrNull ?: 0,
                    height = it["height"]?.jsonPrimitive?.intOrNull ?: 0,
                )
            }
        }.maxByOrNull { it.width }
    }

    private fun parseDuration(text: String?): Int? {
        if (text == null) return null
        val parts = text.split(":").map { it.trim().toIntOrNull() }.filterNotNull()
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> null
        }
    }

    private fun parseYear(text: String?): Int? {
        return text?.split(" • ", ", ")?.mapNotNull { it.trim().toIntOrNull() }?.firstOrNull()
    }

    private fun parseSongCount(text: String?): Int? {
        if (text == null) return null
        return Regex("(\\d+)\\s*songs?").find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun JsonElement.walkPath(vararg keys: String): JsonElement? {
        var current: JsonElement = this
        for (key in keys) {
            current = current.jsonObject?.get(key) ?: return null
        }
        return current
    }

    private fun SearchItem.toBrowseItem(): BrowseItem = when (this) {
        is SearchItem.Song -> BrowseItem.Song(id = id, title = title, thumbnail = thumbnail,
            endpoint = endpoint, subtitle = subtitle, artists = artists, album = album,
            duration = duration, videoId = id)
        is SearchItem.Album -> BrowseItem.Album(id = id, title = title, thumbnail = thumbnail,
            endpoint = endpoint, subtitle = subtitle, year = year, artists = artists)
        is SearchItem.Artist -> BrowseItem.Artist(id = id, title = title, thumbnail = thumbnail,
            endpoint = endpoint, subtitle = subtitle)
        is SearchItem.Playlist -> BrowseItem.Playlist(id = id, title = title, thumbnail = thumbnail,
            endpoint = endpoint, subtitle = subtitle, songCount = songCount)
        is SearchItem.Unknown -> BrowseItem.Unknown(id = id, title = title, thumbnail = thumbnail,
            endpoint = endpoint, subtitle = subtitle)
    }
}
