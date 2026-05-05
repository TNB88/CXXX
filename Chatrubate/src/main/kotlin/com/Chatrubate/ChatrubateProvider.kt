package com.Chatrubate

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.*

class ChatrubateProvider : MainAPI() {
    override var mainUrl              = "https://chaturbate.com"
    override var name                 = "Chatrubate" // Viết đúng chính tả là Chaturbate cơ mà ông lỡ đặt class thế rồi thì cứ để vậy =))
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = false // Live stream thì tải gì ông ơi, tắt đi cho đỡ lỗi
    override val hasChromecastSupport = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "/api/ts/roomlist/room-list/?limit=90" to "Featured",
        "/api/ts/roomlist/room-list/?genders=m&limit=90" to "Male",
        "/api/ts/roomlist/room-list/?genders=f&limit=90" to "Female",
        "/api/ts/roomlist/room-list/?genders=c&limit=90" to "Couples",
        "/api/ts/roomlist/room-list/?genders=t&limit=90" to "Trans",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val offset = if (page == 1) 0 else 90 * (page - 1)
        
        val responseList = app.get("$mainUrl${request.data}&offset=$offset").parsedSafe<Response>()?.rooms?.map { room ->
            LiveSearchResponse(
                name      = room.username,
                url       = "$mainUrl/${room.username}",
                this@ChatrubateProvider.name,
                TvType.Live,
                room.img,
                lang      = null
            )
        } ?: emptyList()

        return newHomePageResponse(
            list = HomePageList(request.name, responseList, isHorizontalImages = true),
            hasNext = true
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<LiveSearchResponse>()

        for (i in 0..3) {
            val results = app.get("$mainUrl/api/ts/roomlist/room-list/?hashtags=$query&limit=90&offset=${i*90}").parsedSafe<Response>()?.rooms?.map { room ->
                LiveSearchResponse(
                    name      = room.username,
                    url       = "$mainUrl/${room.username}",
                    this@ChatrubateProvider.name,
                    TvType.Live,
                    room.img,
                    lang = null
                )
            } ?: emptyList()

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.replace("| PornHoarder.tv","") ?: "Live Stream"
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        return LiveStreamLoadResponse(
            name      = title,
            url       = url,
            apiName   = this.name,
            dataUrl   = url,
            posterUrl = poster,
            plot      = description
        )
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        try {
            val doc = app.get(data).document
            // Web nó hay giấu JSON trong script, mình bới lại cho chuẩn
            val script = doc.select("script").find { item -> item.html().contains("window.initialRoomDossier") } ?: return false
            
            val json = script.html().substringAfter("window.initialRoomDossier = \"").substringBefore(";").unescapeUnicode()
            
            // Regex bắt link m3u8 cứng cáp hơn một chút
            val m3u8Url = """"hls_source"\s*:\s*"(.*?.m3u8)"""".toRegex(RegexOption.IGNORE_CASE).find(json)?.groups?.get(1)?.value ?: return false
            
            // Dùng ExtractorLink chuẩn của API hiện tại
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "Live Stream",
                    url = m3u8Url.replace("\\", ""), // Xóa gạch chéo ngược nếu có
                    referer = "$mainUrl/",
                    quality = Qualities.Unknown.value,
                    isM3u8 = true
                )
            )
            return true
        } catch (e: Exception) {
            logError(e)
            return false
        }
    }

    data class Room(
        @JsonProperty("img")      val img: String      = "",
        @JsonProperty("username") val username: String = "",
        @JsonProperty("subject")  val subject: String  = "",
        @JsonProperty("tags")     val tags: List<String> = arrayListOf()
    )

    data class Response(
        @JsonProperty("all_rooms_count") val all_rooms_count: String      = "",
        @JsonProperty("room_list_id")    val room_list_id: String         = "",
        @JsonProperty("total_count")     val total_count: String          = "",
        @JsonProperty("rooms")           val rooms: List<Room> = arrayListOf()
    )
}

fun String.unescapeUnicode() = replace("\\\\u([0-9A-Fa-f]{4})".toRegex()) {
    String(Character.toChars(it.groupValues[1].toInt(radix = 16)))
}
