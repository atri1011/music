package com.music.myapplication.data.repository.online

import com.music.myapplication.core.common.AppError
import com.music.myapplication.core.common.Result
import com.music.myapplication.core.network.retrofit.TuneHubApi
import com.music.myapplication.data.repository.buildQqGeneralSearchBody
import com.music.myapplication.data.repository.buildQqSearchByTypeBody
import com.music.myapplication.data.repository.buildQqSingerAlbumsBody
import com.music.myapplication.data.repository.extractNeteaseDefaultKeyword
import com.music.myapplication.data.repository.extractNeteaseHotSearchKeywords
import com.music.myapplication.data.repository.extractNeteaseSearchResults
import com.music.myapplication.data.repository.extractQqAlbumSearchResultsFromSongs
import com.music.myapplication.data.repository.extractQqDirectSearchResults
import com.music.myapplication.data.repository.extractQqSearchResults
import com.music.myapplication.data.repository.extractQqSingerAlbumSearchResults
import com.music.myapplication.data.repository.extractKuwoSearchResults
import com.music.myapplication.data.repository.firstStringOf
import com.music.myapplication.data.repository.isLikelySameTitle
import com.music.myapplication.data.repository.mergeQqSearchResultItems
import com.music.myapplication.data.repository.normalizeComparisonText
import com.music.myapplication.data.repository.pickNeteaseHotKeywordToplist
import com.music.myapplication.data.repository.rankQqAlbumSearchResults
import com.music.myapplication.data.repository.toNeteaseOfficialSearchType
import com.music.myapplication.domain.model.Platform
import com.music.myapplication.domain.model.SearchResultItem
import com.music.myapplication.domain.model.SearchSuggestion
import com.music.myapplication.domain.model.SearchType
import com.music.myapplication.domain.model.SuggestionType
import com.music.myapplication.domain.model.Track
import com.music.myapplication.domain.repository.ToplistInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

internal class OnlineMusicSearchDelegate(
    private val api: TuneHubApi,
    private val getToplists: suspend (Platform) -> Result<List<ToplistInfo>>,
    private val getToplistDetailFast: suspend (Platform, String) -> Result<List<Track>>,
    private val officialSearchPageSizeLimit: Int
) {
    suspend fun getHotSearchKeywords(platform: Platform): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val keywords = when (platform) {
                    Platform.NETEASE -> {
                        val detailKeywords = extractNeteaseHotSearchKeywords(
                            runCatching { api.getNeteaseHotSearchDetail() }.getOrNull()
                        )
                        if (detailKeywords.isNotEmpty()) {
                            detailKeywords
                        } else {
                            val legacyKeywords = extractNeteaseHotSearchKeywords(
                                runCatching { api.getNeteaseHotSearch() }.getOrNull()
                            )
                            if (legacyKeywords.isNotEmpty()) legacyKeywords
                            else buildNeteaseFallbackHotKeywords()
                        }
                    }

                    Platform.QQ -> {
                        val resp = api.getQqHotSearch()
                        val root = resp as? JsonObject ?: return@withContext Result.Error(
                            AppError.Api(message = "empty")
                        )
                        val hotkeys = ((root["data"] as? JsonObject)?.get("hotkey") as? JsonArray)
                            ?: JsonArray(emptyList())
                        hotkeys.mapNotNull { (it as? JsonObject)?.firstStringOf("k")?.trim() }
                            .filter { it.isNotBlank() }
                    }

                    else -> emptyList()
                }
                Result.Success(keywords)
            } catch (e: Exception) {
                Result.Error(AppError.Network(cause = e))
            }
        }

    suspend fun getSearchSuggestions(
        platform: Platform,
        keyword: String
    ): Result<List<SearchSuggestion>> = withContext(Dispatchers.IO) {
        try {
            val suggestions = when (platform) {
                Platform.NETEASE -> {
                    val resp = api.getNeteaseSearchSuggest(keyword)
                    val root = resp as? JsonObject ?: return@withContext Result.Success(emptyList())
                    val result = root["result"] as? JsonObject ?: return@withContext Result.Success(emptyList())
                    val list = mutableListOf<SearchSuggestion>()
                    (result["songs"] as? JsonArray)?.forEach { el ->
                        (el as? JsonObject)?.firstStringOf("name")?.let {
                            list += SearchSuggestion(it, SuggestionType.SONG)
                        }
                    }
                    (result["artists"] as? JsonArray)?.forEach { el ->
                        (el as? JsonObject)?.firstStringOf("name")?.let {
                            list += SearchSuggestion(it, SuggestionType.ARTIST)
                        }
                    }
                    (result["albums"] as? JsonArray)?.forEach { el ->
                        (el as? JsonObject)?.firstStringOf("name")?.let {
                            list += SearchSuggestion(it, SuggestionType.ALBUM)
                        }
                    }
                    list
                }

                Platform.QQ -> {
                    val resp = api.getQqSearchSuggest(keyword)
                    val root = resp as? JsonObject ?: return@withContext Result.Success(emptyList())
                    val data = root["data"] as? JsonObject ?: return@withContext Result.Success(emptyList())
                    val list = mutableListOf<SearchSuggestion>()
                    (data["song"] as? JsonObject)?.let { node ->
                        (node["itemlist"] as? JsonArray)?.forEach { el ->
                            (el as? JsonObject)?.firstStringOf("name")?.let {
                                list += SearchSuggestion(it, SuggestionType.SONG)
                            }
                        }
                    }
                    (data["singer"] as? JsonObject)?.let { node ->
                        (node["itemlist"] as? JsonArray)?.forEach { el ->
                            (el as? JsonObject)?.firstStringOf("name")?.let {
                                list += SearchSuggestion(it, SuggestionType.ARTIST)
                            }
                        }
                    }
                    (data["album"] as? JsonObject)?.let { node ->
                        (node["itemlist"] as? JsonArray)?.forEach { el ->
                            (el as? JsonObject)?.firstStringOf("name")?.let {
                                list += SearchSuggestion(it, SuggestionType.ALBUM)
                            }
                        }
                    }
                    list
                }

                else -> emptyList()
            }
            Result.Success(suggestions)
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    suspend fun searchByType(
        platform: Platform,
        keyword: String,
        page: Int,
        pageSize: Int,
        type: SearchType
    ): Result<List<SearchResultItem>> = withContext(Dispatchers.IO) {
        val safeKeyword = keyword.trim()
        if (safeKeyword.isBlank()) return@withContext Result.Success(emptyList())

        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, officialSearchPageSizeLimit)

        try {
            val result = when (platform) {
                Platform.NETEASE -> {
                    val response = api.searchNeteaseByType(
                        keyword = safeKeyword,
                        type = type.toNeteaseOfficialSearchType(),
                        offset = (safePage - 1) * safePageSize,
                        limit = safePageSize
                    )
                    extractNeteaseSearchResults(response, type)
                }

                Platform.QQ -> {
                    fetchQqOfficialSearchResults(safeKeyword, safePage, safePageSize, type)
                }

                Platform.KUWO -> {
                    val response = when (type) {
                        SearchType.ARTIST -> api.searchKuwoArtists(safeKeyword, safePage, safePageSize)
                        SearchType.ALBUM -> api.searchKuwoAlbums(safeKeyword, safePage, safePageSize)
                        SearchType.PLAYLIST -> api.searchKuwoPlaylists(safeKeyword, safePage, safePageSize)
                        SearchType.SONG -> null
                    }
                    extractKuwoSearchResults(response, type)
                }

                Platform.LOCAL -> emptyList()
            }
            Result.Success(result)
        } catch (e: Exception) {
            Result.Error(AppError.Network(cause = e))
        }
    }

    private suspend fun buildNeteaseFallbackHotKeywords(): List<String> {
        val keywords = linkedSetOf<String>()

        extractNeteaseDefaultKeyword(
            runCatching { api.getNeteaseDefaultKeyword() }.getOrNull()
        )?.let(keywords::add)

        val toplists = (getToplists(Platform.NETEASE) as? Result.Success)?.data.orEmpty()
        val candidate = pickNeteaseHotKeywordToplist(toplists)
        val tracks = candidate?.let { toplist ->
            (getToplistDetailFast(Platform.NETEASE, toplist.id) as? Result.Success)?.data.orEmpty()
        }.orEmpty()

        tracks.forEach { track ->
            track.title.trim().takeIf(String::isNotBlank)?.let(keywords::add)
            if (keywords.size >= 20) return keywords.toList()
        }

        if (keywords.isNotEmpty()) return keywords.toList()
        return toplists.mapNotNull { it.name.trim().takeIf(String::isNotBlank) }.distinct().take(10)
    }

    private suspend fun fetchQqOfficialSearchResults(
        keyword: String,
        page: Int,
        pageSize: Int,
        type: SearchType
    ): List<SearchResultItem> {
        if (type == SearchType.PLAYLIST) {
            val response = api.postQqMusicu(
                buildQqSearchByTypeBody(
                    keyword = keyword,
                    page = page,
                    pageSize = pageSize,
                    type = type
                )
            )
            return extractQqSearchResults(response, type)
        }

        val generalResponse = api.postQqMusicu(
            buildQqGeneralSearchBody(
                keyword = keyword,
                page = page,
                pageSize = pageSize
            )
        )

        val directResults = extractQqDirectSearchResults(generalResponse, type)
        if (type != SearchType.ALBUM && directResults.isNotEmpty()) return directResults

        if (type == SearchType.ALBUM) {
            if (directResults.isNotEmpty()) {
                return rankQqAlbumSearchResults(keyword, directResults)
            }
            val generalAlbumResults = mergeQqSearchResultItems(
                extractQqSearchResults(generalResponse, type),
                extractQqAlbumSearchResultsFromSongs(generalResponse)
            )
            val normalizedKeyword = keyword.normalizeComparisonText()
            if (generalAlbumResults.any { it.title.normalizeComparisonText() == normalizedKeyword }) {
                return rankQqAlbumSearchResults(keyword, generalAlbumResults)
            }

            val directSinger = extractQqDirectSearchResults(
                generalResponse,
                SearchType.ARTIST
            ).firstOrNull()
            if (directSinger != null && directSinger.title.isLikelySameTitle(keyword)) {
                val albumsResponse = api.postQqMusicu(
                    buildQqSingerAlbumsBody(
                        singerMid = directSinger.id,
                        page = page,
                        pageSize = pageSize
                    )
                )
                val singerAlbums = extractQqSingerAlbumSearchResults(albumsResponse)
                if (singerAlbums.isNotEmpty()) {
                    return rankQqAlbumSearchResults(
                        keyword,
                        mergeQqSearchResultItems(singerAlbums, generalAlbumResults)
                    )
                }
            }
        }

        val typedResponse = api.postQqMusicu(
            buildQqSearchByTypeBody(
                keyword = keyword,
                page = page,
                pageSize = pageSize,
                type = type
            )
        )
        val typedResults = extractQqSearchResults(typedResponse, type)
        return if (type == SearchType.ALBUM) {
            rankQqAlbumSearchResults(
                keyword,
                mergeQqSearchResultItems(
                    directResults,
                    extractQqSearchResults(generalResponse, type),
                    extractQqAlbumSearchResultsFromSongs(generalResponse),
                    typedResults
                )
            )
        } else {
            typedResults
        }
    }
}
