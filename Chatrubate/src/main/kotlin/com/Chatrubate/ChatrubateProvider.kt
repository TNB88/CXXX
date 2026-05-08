package com.Chatrubate

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.*

class ChatrubateProvider : MainAPI() {
    override var mainUrl              = "https://chaturbate.com"
    override var name                 = "Chatrubate"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = false 
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
            // Nhét thêm mớ Header này vào để giả lập trình duyệt xịn, web nó không đá ra
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                "Accept-Language" to "en-US,en;q=0.5"
            )

            val html = app.get(data, headers = headers).text
            
            // Ép giải mã sạch sẽ bọn \u002F thành dấu gạch chéo hết
            val cleanHtml = html.unescapeUnicode().replace("\\/", "/")
            
            // Giờ code nó sạch sẽ rồi thì regex quét vô tư
            val m3u8Regex = """(https?://[^"'\s\\]+\.m3u8)""".toRegex(RegexOption.IGNORE_CASE)
            val m3u8Url = m3u8Regex.find(cleanHtml)?.groups?.get(1)?.value
            
            if (m3u8Url != null) {
                callback.invoke(
                    ExtractorLink(
                        source = name,
                        name = "Live Stream",
                        url = m3u8Url,
                        referer = data, // Trỏ thẳng referer về cái link room luôn cho chuẩn
                        quality = Qualities.Unknown.value,
                        isM3u8 = true
                    )
                )
                return true
            }
            return false
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

// Hàm này tôi viết lại rồi để ngoài cùng file luôn nhé, decode siêu cứng
fun String.unescapeUnicode(): String {
    val regex = """\\u([0-9a-fA-F]{4})""".toRegex()
    return regex.replace(this) {
        it.groupValues[1].toInt(16).toChar().toString()
    }
}package com.Chatrubate

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.*

class ChatrubateProvider : MainAPI() {
    override var mainUrl              = "https://chaturbate.com"
    override var name                 = "Chatrubate" 
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = false 
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
            // Lấy thẳng text raw HTML thay vì dùng Jsoup Document, để mã script không bị xào nấu mất form
            val html = app.get(data).text
            
            // Bọn nó hay escape kiểu "https:\/\/bcp.chaturbate.com\/...", mình replace hết để về link sạch
            val cleanHtml = html.replace("\\/", "/")
            
            // Quét thẳng tay tóm ngay thằng m3u8 trong đống bùi nhùi đó
            val m3u8Regex = """(https?://[^"'\s]+\.m3u8)""".toRegex(RegexOption.IGNORE_CASE)
            val m3u8Url = m3u8Regex.find(cleanHtml)?.groups?.get(1)?.value ?: return false
            
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "Live Stream",
                    url = m3u8Url,
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
