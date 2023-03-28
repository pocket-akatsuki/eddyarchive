package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import okhttp3.Interceptor
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.runBlocking
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.lang.Math.ceil

class MacIPTVProvider : MainAPI() {
    private var defaulmacAdresse =
        "mac=00:1A:79:aa:53:65"//"mac=00:1A:79:31:ed:e5"//"mac=00:1A:79:28:9C:Be"//"mac=00%3A1a%3A79%3Aae%3A2a%3A30"//
    private val defaultmainUrl =
        "http://ky-iptv.com:25461/portalstb"//"http://nas.bordo1453.be"//"http://infinitymedia.live:8880"//"http://ultra-box.club"//
    private var defaultname = "Test-Account MacIPTV"
    private var Basename = "MacIPTV \uD83D\uDCFA"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override var lang = "en"
    override var supportedTypes =
        setOf(TvType.Live, TvType.Movie, TvType.TvSeries, TvType.Anime) // live
    private var isNotInit = true
    private var key: String? = "" // key used in the header
    private var allCategory =
        listOf<String>() // even if we don't display all countries or categories, we need to know those avalaible
    private var helpVid: String = ""
    private var helpTag: String = ""
    private var helpAcc: String = ""

    init {
        defaultname = "Test-Account $Basename "
        name = Basename
    }

    private fun List<Js_category>.getGenreNCategoriesInit(section: String): List<MainPageData> {
        allCategory =
            allCategory + ("|\uD83C\uDD70\uD83C\uDD7B\uD83C\uDD7B $section \uD83C\uDDF9\u200B\u200B\u200B\u200B\u200B\uD83C\uDDE6\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEC\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF8\u200B\u200B\u200B\u200B\u200B|")
        var allCat = listOf<String>()
        val result = this.mapNotNull { js ->
            val idGenre = js.id.toString()
            val categoryTitle = js.title.toString()
            cleanTitle(
                categoryTitle.replace("&", "").replace(",", "").replace(":", "")
                    .replace("#", "").replace("|", "").replace("*", "").replace("/", "")
                    .replace("\\", "").replace("[", "").replace("]", "")
                    .replace("(", "").replace(")", "")
            ).split("""\s+""".toRegex()).forEach { titleC ->
                if (!allCat.any { it.contains(titleC, true) }) {
                    allCat = allCat + ("|$titleC|")
                }
            }

            if (idGenre.contains("""\d+""".toRegex()) && categoryTitle.uppercase()
                    .contains(findKeyWord(tags.toString()))
            ) {
                val nameGenre = cleanTitle(getFlag(categoryTitle)).trim()

                MainPageData(nameGenre, idGenre)
            } else {
                null
            }
        }
        allCategory = allCategory + allCat.sortedBy { it }
        return result
    }

    /**
    check if the data are incorrect
     **/
    private fun accountInfoNotGood(url: String, mac: String?): Boolean {
        return url.uppercase().trim() == "NONE" || url.isBlank() || mac?.uppercase()
            ?.trim() == "NONE" || mac.isNullOrBlank()
    }

    /**
    Sometimes providers ask a key (token) in the headers
     **/
    private suspend fun getkey(mac: String) {
        val adresseMac = if (!mac.contains("mac=")) {
            "mac=$mac"
        } else {
            mac
        }
        val url_key =
            "$mainUrl/portal.php?type=stb&action=handshake&token=&JsHttpRequest=1-xml"
        val reponseGetkey = app.get(
            url_key, headers = mapOf(
                "Cookie" to adresseMac,
                "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
                "X-User-Agent" to "Model: MAG250; Link: WiFi",
            )
        )
        key = tryParseJson<Getkey>(
            Regex("""\{\"js\"(.*[\r\n]*)+\}""").find(reponseGetkey.text)?.groupValues?.get(0)
        )?.js?.token ?: ""
    }

    /**
    From user account, get the tags (to select categories and countries), url , mac address ... if there are avalaible
     **/
    private suspend fun getAuthHeader() {
        tags = tags ?: "" // tags will allow to select more contains
        if (tags?.uppercase()?.trim() == "NONE" || tags?.isBlank() == true) tags = ".*"
        tags = tags?.uppercase()
        mainUrl = overrideUrl.toString()
        mainUrl = when { // the "c" is not needed and some provider doesn't work with it
            mainUrl.endsWith("/c/") -> mainUrl.dropLast(3)
            mainUrl.endsWith("/c") -> mainUrl.dropLast(2)
            mainUrl.endsWith("/") -> mainUrl.dropLast(1)
            else -> mainUrl
        }
        val isNotGood = accountInfoNotGood(mainUrl, loginMac)
        if (isNotGood) {
            mainUrl = defaultmainUrl
            name = defaultname
        } else {
            name = ("$companionName $Basename") + " |${lang.uppercase()}|"
            defaulmacAdresse = "mac=$loginMac"
        }
        headerMac = when {
            isNotGood -> { // do this if mainUrl or mac adresse is blank
                getkey(defaulmacAdresse)
                if (key?.isNotBlank() == true) {
                    mutableMapOf(
                        "Cookie" to defaulmacAdresse,
                        "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
                        "Authorization" to "Bearer $key",
                        "X-User-Agent" to "Model: MAG250; Link: WiFi",
                    )
                } else {
                    mutableMapOf(
                        "Cookie" to defaulmacAdresse,
                        "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
                        "X-User-Agent" to "Model: MAG250; Link: WiFi",
                    )
                }
            }
            else -> {

                getkey(loginMac.toString())
                if (key?.isNotBlank() == true) {
                    mutableMapOf(
                        "Cookie" to "mac=$loginMac",
                        "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
                        "Authorization" to "Bearer $key",
                        "X-User-Agent" to "Model: MAG250; Link: WiFi",
                    )
                } else {
                    mutableMapOf(
                        "Cookie" to "mac=$loginMac",
                        "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
                        "X-User-Agent" to "Model: MAG250; Link: WiFi",
                        /*       "Connection" to "Keep-Alive",
                               "Accept-Encoding" to "gzip",
                               "Cache-Control" to "no-cache",*/
                    )
                }
            }
        }
        app.get(
            "$mainUrl/portal.php?type=stb&action=get_modules",
            headers = headerMac
        ) // some providers need this request to work

        listOf(
            "https://github.com/Eddy976/cloudstream-extensions-eddy/issues/4",
            "https://github.com/Eddy976/cloudstream-extensions-eddy/issues/3",
            "https://github.com/Eddy976/cloudstream-extensions-eddy/issues/2",
        ).apmap { url ->
            when (url.takeLast(1)) {
                "4" -> helpVid = app.get(url).document.select("video").attr("src")
                "3" -> helpTag = app.get(url).document.select("video").attr("src")
                "2" -> helpAcc = app.get(url).document.select("video").attr("src")
                else -> ""
            }
        } //4 search
        isNotInit = false
    }


    data class Channel(
        var title: String,
        var url: String,
        val url_image: String?,
        val lang: String?,
        var id: String?,
        var tv_genre_id: String?,
        var ch_id: String?,
        var description: String? = null,
        var actors: String? = null,
        var year: String? = null,
        var rating: String? = null,
        var is_M: Int? = null,
        var genres_str: String? = null,
        var series: ArrayList<String> = arrayListOf(),
    )


    private suspend fun createArrayChannel(
        idGenre: String,
        type: String,
        load: Boolean = true,
        rquery: String = ""
    ): Sequence<String> {
        val rgxFindJson =
            Regex("""\{[\s]*\"js\"(.*[\r\n]*)+\}""")
        var res = sequenceOf<String>()
        when (type) {
            "all" -> {
                val url = "$mainUrl/portal.php?type=itv&action=get_all_channels"
                tryParseJson<RootITV>(
                    rgxFindJson.find(
                        app.get(url, headers = headerMac).text
                    )?.groupValues?.get(
                        0
                    )
                )?.js?.data?.forEach { data ->
                    if (FuzzySearch.ratio(
                            cleanTitleButKeepNumber(data.name.toString()).lowercase(),
                            rquery.lowercase()
                        ) > 40
                    ) {
                        res += sequenceOf(
                            Channel(
                                data.name.toString(),
                                "http://localhost/ch/${data.id}" + "_",
                                data.logo?.replace("""\""", ""),
                                "",
                                data.id,
                                data.tvGenreId,
                                data.cmds[0].chId
                            ).toJson()
                        )
                    }

                }
                List(2) { it + 1 }.apmap {
                    listOf(
                        "$mainUrl/portal.php?type=vod&action=get_ordered_list&p=$it&search=$rquery&sortby=added",
                        "$mainUrl/portal.php?type=series&action=get_ordered_list&p=$it&search=$rquery&sortby=added"
                    ).apmap { url ->
                        tryParseJson<RootVoDnSeries>(
                            rgxFindJson.find(
                                app.get(
                                    url,
                                    headers = headerMac
                                ).text
                            )?.groupValues?.get(
                                0
                            )
                        )?.js?.data!!.forEach { data ->
                            val isMovie: String
                            val namedata = if (url.contains("type=vod")) {
                                isMovie = "&vod"
                                data.name.toString()

                            } else {
                                isMovie = "&series"
                                data.path.toString()

                            }
                            res += sequenceOf(
                                Channel(
                                    namedata + isMovie,
                                    data.cmd.toString(),
                                    data.screenshotUri?.replace("""\""", ""),
                                    "",
                                    data.id,
                                    data.categoryId,
                                    data.id,
                                    data.description,
                                    data.actors,
                                    if (data.year?.split("-")?.isNotEmpty() == true) {
                                        data.year!!.split("-")[0]
                                    } else {
                                        data.year
                                    },//2022-02-01
                                    data.ratingIm,//2.3
                                    1,
                                    data.genresStr,
                                    data.series,
                                ).toJson()
                            )
                        }
                    }
                }
            }
            "itv" -> {
                val url = "$mainUrl/portal.php?type=itv&action=get_all_channels"
                //"$mainUrl/portal.php?type=itv&action=get_ordered_list&genre=$idGenre&force_ch_link_check=&fav=0&sortby=number&hd=0&p=1"
                tryParseJson<RootITV>(
                    rgxFindJson.find(
                        app.get(url, headers = headerMac).text
                    )?.groupValues?.get(
                        0
                    )
                )?.js?.data?.forEach { data ->
                    if (data.tvGenreId == idGenre) {
                        res += sequenceOf(
                            Channel(
                                data.name.toString(),
                                "http://localhost/ch/${data.id}" + "_",
                                data.logo?.replace("""\""", ""),
                                "",
                                data.id,
                                data.tvGenreId,
                                data.cmds[0].chId
                            ).toJson()
                        )
                    }

                }
            }
            "vod", "series" -> {
                val url =
                    "$mainUrl/portal.php?type=$type&action=get_ordered_list&category=$idGenre&movie_id=0&season_id=0&episode_id=0&p=1&sortby=added"
                val getJs = tryParseJson<RootVoDnSeries>(
                    rgxFindJson.find(
                        app.get(url, headers = headerMac).text
                    )?.groupValues?.get(
                        0
                    )
                )?.js
                val x = getJs?.totalItems!!.toDouble() / getJs.maxPageItems!!
                when (load) {
                    true -> {
                        getJs.data.forEach { data ->
                            val isMovie: Int
                            val namedata = if (type == "vod") {
                                isMovie = 1
                                data.name.toString()

                            } else {
                                isMovie = 0
                                data.path.toString()

                            }
                            res += sequenceOf(
                                Channel(
                                    namedata,
                                    data.cmd.toString(),
                                    data.screenshotUri?.replace("""\""", ""),
                                    "",
                                    data.id,
                                    data.categoryId,
                                    data.id,
                                    data.description,
                                    data.actors,
                                    if (data.year?.split("-")?.isNotEmpty() == true) {
                                        data.year!!.split("-")[0]
                                    } else {
                                        data.year
                                    },//2022-02-01
                                    data.ratingIm,//2.3
                                    isMovie,//isMovie
                                    data.genresStr,// "Fantasy, Action, Adventure",
                                    data.series,
                                ).toJson()
                            )
                        }
                    }
                    else -> {
                        val takeN = ceil(x).toInt()

                        List(takeN) { it + 1 }.apmap {
                            tryParseJson<RootVoDnSeries>(
                                rgxFindJson.find(
                                    app.get(
                                        "$mainUrl/portal.php?type=$type&action=get_ordered_list&category=$idGenre&movie_id=0&season_id=0&episode_id=0&p=$it&sortby=added",
                                        headers = headerMac
                                    ).text
                                )?.groupValues?.get(
                                    0
                                )
                            )?.js?.data!!.forEach { data ->
                                val isMovie: Int
                                val namedata = if (type == "vod") {
                                    isMovie = 1
                                    data.name.toString()

                                } else {
                                    isMovie = 0
                                    data.path.toString()

                                }
                                res += sequenceOf(
                                    Channel(
                                        namedata,
                                        data.cmd.toString(),
                                        data.screenshotUri?.replace("""\""", ""),
                                        "",
                                        data.id,
                                        data.categoryId,
                                        data.id,
                                        data.description,
                                        data.actors,
                                        if (data.year?.split("-")?.isNotEmpty() == true) {
                                            data.year!!.split("-")[0]
                                        } else {
                                            data.year
                                        },//2022-02-01
                                        data.ratingIm,//2.3
                                        isMovie,
                                        data.genresStr,
                                        data.series,
                                    ).toJson()
                                )
                            }

                        }
                    }
                }


            }
            else -> {
                return res
            }
        }
        return res
    }


    private suspend fun findepisode(idGenre: String, id: String): List<Channel> {

        val res = tryParseJson<RootVoDnSeries>(
            Regex("""\{[\s]*\"js\"(.*[\r\n]*)+\}""").find(
                app.get(
                    "$mainUrl/portal.php?type=series&action=get_ordered_list&category=$idGenre&movie_id=$id&season_id=&episode_id=&p=0&sortby=added",
                    headers = headerMac
                ).text
            )?.groupValues?.get(
                0
            )
        )?.js?.data!!.map { data ->
            Channel(
                data.path.toString(),
                data.cmd.toString(),
                data.screenshotUri?.replace("""\""", ""),
                "",
                data.id,
                data.categoryId,
                data.id,
                data.description,
                data.actors,
                if (data.year?.split("-")?.isNotEmpty() == true) {
                    data.year!!.split("-")[0]
                } else {
                    data.year
                },//2022-02-01
                data.ratingIm,//2.3
                0,
                data.genresStr,
                data.series,
            )
        }
        return res
    }

    fun helpSearch(): List<SearchResponse> {
        return listOf(
            Pair(
                "Find a media",
                listOf(
                    helpVid,
                    "https://www.jharkhanditsolutions.com/wp-content/uploads/2020/07/GettyImages-1047578412-692fa117cf86450287d8873eeb1a95c8-aa8d654cec814174a9e07bdae85a1eb7.jpg",
                    "search"
                )
            )
        ).map {
            val title = it.first
            val url = it.second[0]
            val urlImage = it.second[1]
            val type = it.second[2]
            val catInfo = CategorieInfo(
                title,
                url,
                type,
            )
            TvSeriesSearchResponse(
                title,
                "${mainUrl}${catInfo.toJson()}",
                name,
                TvType.TvSeries,
                urlImage,
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val queryCode = query.split("&")
        var rquery: String? = null
        val idGenre: String?
        val type: String?
        when (queryCode.size) {
            3 -> {
                idGenre = queryCode[1]
                type = when (queryCode[0].toIntOrNull()) {
                    0 -> "itv"
                    1 -> "vod"
                    2 -> "series"
                    else -> null
                }
                rquery = queryCode[2]
            }
            2 -> {
                idGenre = queryCode[1]
                type = when (queryCode[0].toIntOrNull()) {
                    0 -> "itv"
                    1 -> "vod"
                    2 -> "series"
                    else -> null
                }
            }
            1 -> {
                idGenre = "0"
                type = "all"
                rquery = query
            }

            else -> {
                return helpSearch()
            }
        }
        if (type != null) {

            val arrayCh = createArrayChannel(
                idGenre.toString(),
                type.toString(),
                false,
                rquery.toString()
            )
            val resSeq =
                if (rquery.isNullOrBlank()) {
                    arrayCh
                } else {
                    arrayCh.sortedBy {
                        val name =
                            cleanTitleButKeepNumber(parseJson<Channel>(it).title).trim()
                        -FuzzySearch.ratio(name.lowercase(), rquery.toString().lowercase())
                    }.take(100)
                }.map {
                    val media = parseJson<Channel>(it)
                    val titleM = media.title
                    val typeM = if (type == "all") {
                        when {
                            titleM.contains("&vod") -> "vod"
                            titleM.contains("&series") -> "series"
                            else -> "itv"
                        }
                    } else {
                        type.toString()
                    }
                    val streamurl = CategorieInfo(
                        titleM.replace("&vod", "").replace("&series", ""),
                        media.tv_genre_id.toString(),
                        typeM,
                        media,
                    ).toJson()
                    val uppername = titleM.replace("&vod", "").replace("&series", "").uppercase()
                    val quality = getQualityFromString(
                        when (uppername.isNotBlank()) {
                            uppername.contains(findKeyWord("UHD")) -> {
                                "UHD"
                            }
                            uppername.contains(findKeyWord("HD")) -> {
                                "HD"
                            }
                            uppername.contains(findKeyWord("SD")) -> {
                                "SD"
                            }
                            uppername.contains(findKeyWord("FHD")) -> {
                                "HD"
                            }
                            uppername.contains(findKeyWord("4K")) -> {
                                "FourK"
                            }

                            else -> {
                                null
                            }
                        }
                    )
                    LiveSearchResponse(
                        "[${media.tv_genre_id.toString()}]${
                            getFlag(
                                titleM.replace("&vod", "").replace("&series", "")
                            )
                        }",
                        streamurl,
                        name,
                        TvType.Live,
                        media.url_image,
                        quality = quality,
                    )
                }
            if (resSeq.toList().isEmpty()) {
                return helpSearch()
            } else {
                return resSeq.toList()
            }
        } else {
            return helpSearch()
        }

    }

    data class Root_epg(
        @JsonProperty("js") var js: ArrayList<Js_epg> = arrayListOf()
    )

    data class Js_epg(
        @JsonProperty("name") var name: String? = null,
        @JsonProperty("descr") var descr: String? = null,
        @JsonProperty("t_time") var tTime: String? = null,
        @JsonProperty("t_time_to") var tTimeTo: String? = null,
    )

    private fun getEpg(response: String): String { // get the EPG to have get the schedual
        val reponseJSON_0 = tryParseJson<Root_epg>(response)
        var description = ""
        reponseJSON_0?.js?.forEach { epg_i ->
            val name = epg_i.name
            val descr = epg_i.descr
            val t_time = epg_i.tTime
            val t_time_to = epg_i.tTimeTo
            val new_descr = "||($t_time -> $t_time_to)  $name : $descr"
            if (!description.contains(new_descr)) {
                description = "$description\n $new_descr"
            }

        }
        return description
    }


    override suspend fun load(url: String): LoadResponse {
        val media = parseJson<CategorieInfo>(url.replace(mainUrl, ""))
        val type = media.type

        when (type) {
            "search" -> { // how to create an iptv account
                return MovieLoadResponse(
                    name = "How to search",
                    url = media.toJson(),
                    apiName = name,
                    TvType.Movie,
                    dataUrl = media.id,
                    posterUrl = "https://www.toutestpossible.be/wp-content/uploads/2017/05/comment-faire-des-choix-eclaires-en-10-etapes-01-300x167.jpg",
                    plot = "To see all the content of the section (➍➊➒) from movie(code=➊), then go search ➊&➍➊➒. To search the movie \uD83C\uDDE6\u200B\u200B\u200B\u200B\u200B\uD83C\uDDFB\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEA\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF3\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEC\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEA\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF7\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF8\u200B\u200B\u200B\u200B\u200B in ➍➊➒ write: ➊&➍➊➒&\uD83C\uDDE6\u200B\u200B\u200B\u200B\u200B\uD83C\uDDFB\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEA\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF3\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEC\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEA\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF7\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF8\u200B\u200B\u200B\u200B\u200B",
                )
            }
            "account" -> { // how to create an iptv account
                return MovieLoadResponse(
                    name = "GO TO CREATE YOUR IPTV ACCOUNT",
                    url = media.toJson(),
                    apiName = name,
                    TvType.Movie,
                    dataUrl = media.id,
                    posterUrl = "https://www.toutestpossible.be/wp-content/uploads/2017/05/comment-faire-des-choix-eclaires-en-10-etapes-01-300x167.jpg",
                    plot = "Find a site where there are IPTV stb/stalker accounts (url + mac address) and create your account",
                )
            }
            "tags" -> { // go to see all the avalaible tags
                return MovieLoadResponse(
                    name = "GO TO CREATE YOUR \uD83C\uDDF9\u200B\u200B\u200B\u200B\u200B\uD83C\uDDE6\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEC\u200B\u200B\u200B\u200B\u200B ACCOUNT e.g. italia|sport|crime|uk ",
                    url = media.toJson(),
                    apiName = name,
                    TvType.Movie,
                    dataUrl = media.id,
                    posterUrl = "https://www.toutestpossible.be/wp-content/uploads/2017/05/comment-faire-des-choix-eclaires-en-10-etapes-01-300x167.jpg",
                    plot = "ALL TAGS \uD83D\uDD0E $allCategory",

                    )
            }
            "error" -> { // case where the provider don't work
                return MovieLoadResponse(
                    name = "\uD83C\uDDF5\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF7\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF4\u200B\u200B\u200B\u200B\u200B\uD83C\uDDE7\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF1\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEA\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF2\u200B\u200B\u200B\u200B\u200B",
                    url = url,
                    apiName = this.name,
                    TvType.Movie,
                    dataUrl = url,
                    posterUrl = "https://www.toutestpossible.be/wp-content/uploads/2017/05/comment-faire-des-choix-eclaires-en-10-etapes-01-300x167.jpg",
                    plot = "There is an issue with this account. Please see the tags and create an account. Otherwise check your credentials or change your DNS or use a VPN. In the worst case try with another account",
                    comingSoon = true,

                    )
            }
            else -> {
                val idGenre = media.id
                val isNothing = media.dataStream?.url.isNullOrBlank()
                val dataUrl = if (isNothing) {
                    helpVid
                } else {
                    media.dataStream?.url.toString()
                }
                var recommendations = if (isNothing) {
                    createArrayChannel(
                        idGenre,
                        type.toString()
                    ).map {
                        val channel = parseJson<Channel>(it)
                        val channelname = channel.title
                        val posterurl = channel.url_image.toString()
                        val streamurl = CategorieInfo(
                            channelname,
                            idGenre,
                            type,
                            channel,
                        ).toJson()
                        val uppername = channelname.uppercase()
                        val quality = getQualityFromString(
                            when (channelname.isNotBlank()) {
                                uppername.contains(findKeyWord("UHD")) -> {
                                    "UHD"
                                }
                                uppername.contains(findKeyWord("HD")) -> {
                                    "HD"
                                }
                                uppername.contains(findKeyWord("SD")) -> {
                                    "SD"
                                }
                                uppername.contains(findKeyWord("FHD")) -> {
                                    "HD"
                                }
                                uppername.contains(findKeyWord("4K")) -> {
                                    "FourK"
                                }

                                else -> {
                                    null
                                }
                            }
                        )
                        LiveSearchResponse(
                            name = cleanTitleButKeepNumber(channelname),
                            url = streamurl,
                            name,
                            TvType.Live,
                            posterUrl = posterurl,
                            quality = quality,
                        )

                    }
                } else {
                    null
                }

                val plot = if (isNothing) {
                    "ALL \uD83C\uDDFB\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEE\u200B\u200B\u200B\u200B\u200B\uD83C\uDDE9\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEA\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF4\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF8\u200B\u200B\u200B\u200B\u200B \uD83D\uDC49 search \uD83D\uDD0D ${media.title}"
                } else {
                    media.dataStream?.description
                }
                when (type) {
                    "vod" -> {
                        return MovieLoadResponse(
                            media.title,
                            media.toJson(),
                            apiName = this.name,
                            TvType.Movie,
                            dataUrl,
                            media.dataStream?.url_image,
                            media.dataStream?.year?.toIntOrNull(),
                            plot,
                            media.dataStream?.rating?.toIntOrNull(),
                            tags = media.dataStream?.genres_str?.split(","),
                            recommendations = recommendations?.toList(),

                            )
                    }
                    "series" -> {
                        var episodes =

                            findepisode(
                                media.dataStream?.tv_genre_id.toString(),
                                media.dataStream?.id.toString()
                            ).map { channel ->
                                val season = if (channel.id?.contains(":") == true) {
                                    channel.id?.replace("""[\s]*\d+\:""".toRegex(), "")
                                        ?.toIntOrNull()
                                } else {
                                    null
                                }
                                channel.series.map {
                                    Episode(
                                        channel.url + "&series=$it",
                                        episode = it.toIntOrNull(),
                                        season = season,
                                        name = "Episode $it",
                                        description = channel.description,
                                        posterUrl = channel.url_image,
                                        rating = channel.rating?.toIntOrNull()

                                    )
                                }
                            }.flatten()
                        val isEmptyEp = episodes.isEmpty()
                        if (isEmptyEp) {
                            episodes = listOf(
                                Episode(
                                    dataUrl,
                                    episode = 1,
                                    season = null,
                                    name = "Ⓣⓤⓣⓞ",
                                    description = "To see all the content of the section (➍➊➒) from movie(code=➊), then go search ➊&➍➊➒. To search  \uD83C\uDDE6\u200B\u200B\u200B\u200B\u200B\uD83C\uDDFB\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEA\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF3\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEC\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEA\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF7\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF8\u200B\u200B\u200B\u200B\u200B in ➍➊➒ write: ➊&➍➊➒&\uD83C\uDDE6\u200B\u200B\u200B\u200B\u200B\uD83C\uDDFB\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEA\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF3\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEC\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEA\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF7\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF8\u200B\u200B\u200B\u200B\u200B",
                                    posterUrl = "https://www.jharkhanditsolutions.com/wp-content/uploads/2020/07/GettyImages-1047578412-692fa117cf86450287d8873eeb1a95c8-aa8d654cec814174a9e07bdae85a1eb7.jpg",
                                )
                            )
                        } else {
                            recommendations = null
                        }


                        return TvSeriesLoadResponse(
                            media.title,
                            media.toJson(),
                            apiName = name,
                            TvType.TvSeries,
                            episodes,
                            media.dataStream?.url_image,
                            media.dataStream?.year?.toIntOrNull(),
                            if (isEmptyEp) {
                                "ALL \uD83C\uDDF8\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEA\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF7\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEE\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEA\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF8\u200B\u200B\u200B\u200B\u200B \uD83D\uDC49 search \uD83D\uDD0D ${media.title}"
                            } else {
                                media.dataStream?.description
                            },
                            null,
                            media.dataStream?.rating?.toIntOrNull(),
                            tags = media.dataStream?.genres_str?.split(","),
                            recommendations = recommendations?.toList(),
                        )
                    }
                    else -> {
                        val description = if (!media.dataStream?.ch_id.isNullOrBlank()) {
                            val epg_url =
                                "$mainUrl/portal.php?type=$type&action=get_short_epg&ch_id=${media.dataStream?.ch_id}&size=10&JsHttpRequest=1-xml" // the plot when it's avalaible
                            val response = app.get(epg_url, headers = headerMac)
                            getEpg(response.text)
                        } else {
                            "ALL \uD83C\uDDF1\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEE\u200B\u200B\u200B\u200B\u200B\uD83C\uDDFB\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEA\u200B\u200B\u200B\u200B\u200B \uD83C\uDDF9\u200B\u200B\u200B\u200B\u200B\uD83C\uDDFB\u200B\u200B\u200B\u200B\u200B \uD83D\uDC49 in recommendations or search \uD83D\uDD0D ${media.title}"
                        }

                        val title = media.title
                        val posterUrl = media.dataStream?.url_image.toString()

                        return LiveStreamLoadResponse(
                            name = title,
                            url = media.toJson(),
                            apiName = this.name,
                            dataUrl = dataUrl,
                            posterUrl = posterUrl,
                            plot = description,
                            recommendations = recommendations?.toList(),
                            rating = media.dataStream?.rating?.toIntOrNull(),
                            tags = media.dataStream?.genres_str?.split(","),
                        )
                    }
                }

            }

        }

    }


    /**
     * Use new token.
     * */
    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        // Needs to be object instead of lambda to make it compile correctly
        return object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
                val request = chain.request()
                if (request.headers.get("Cookie")
                        ?.contains("localhost") == true || request.headers.get("Cookie")
                        ?.contains("ey") == true
                ) {
                    val chID = request.headers.get("Cookie")
                    //val chID = Regex("""\/(\d*)\?""").find(request.url.toString())!!.groupValues[1] + "_"
                    val TokenLink = when {
                        chID?.contains("cmd=") == true -> "$mainUrl/portal.php?type=vod&action=create_link&$chID"
                        else -> "$mainUrl/portal.php?type=itv&action=create_link&forced_storage=undefined&download=0&cmd=ffmpeg%20$chID"///portal.php?type=itv&action=create_link&cmd=ffmpeg%20$chID&series=&forced_storage=0&disable_ad=0&download=0&force_ch_link_check=0&JsHttpRequest=1-xml"

                    }
                    var link: String
                    var lien: String
                    runBlocking {
                        val getTokenLink = app.get(TokenLink, headers = headerMac).text
                        val regexGetLink = Regex("""(http[^,]*)\"[\}]{0,1},""")
                        link = regexGetLink.find(getTokenLink)?.groupValues?.get(1).toString()
                            .replace(
                                """\""",
                                ""
                            )
                        lien = link
                        //link.contains("extension")|| link.contains("ext") || link.contains("movie") || link.contains("serie")
                        val headerlocation = app.get(
                            link,
                            allowRedirects = false
                        ).headers
                        val redirectlink = headerlocation["location"]
                            .toString()
                        if (redirectlink != "null") {
                            lien = redirectlink
                        }
                    }

                    val newRequest = chain.request()
                        .newBuilder().url(lien).build()
                    return chain.proceed(newRequest)
                } else {
                    return chain.proceed(chain.request())
                }

            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        if (data.contains("githubusercontent") && data.contains(".mp4")) {
            callback.invoke(
                ExtractorLink(
                    name,
                    "TUTO",
                    data,
                    "",
                    Qualities.Unknown.value,
                )
            )
            return true
        }
        val headCode: String
        val TokenLink = when {
            data.take(2).contains("ey") -> {
                headCode = "cmd=$data"
                "$mainUrl/portal.php?type=vod&action=create_link&$headCode"
            }
            else -> {
                headCode = data
                "$mainUrl/portal.php?type=itv&action=create_link&forced_storage=undefined&download=0&cmd=ffmpeg%20$data"//&series=&forced_storage=undefined&disable_ad=0&download=0&force_ch_link_check=0"
            }

        }

        val getTokenLink = app.get(TokenLink, headers = headerMac).text
        val regexGetLink = Regex("""(http[^,]*)\"[\}]{0,1},""")
        val link =
            regexGetLink.find(getTokenLink)?.groupValues?.get(1).toString().replace("""\""", "")
        val head = mapOf(
            "Accept" to "*/*",
            "Accept-Language" to "en_US",
            "User-Agent" to "VLC/3.0.18 LibVLC/3.0.18",
            "Range" to "bytes=0-",
            "Cookie" to "$headCode"
        )
        val lien = when {
            true -> {//link.contains("extension")|| link.contains("ext") || link.contains("movie") || link.contains("serie")
                val headerlocation = app.get(
                    link,
                    headers = head,
                    allowRedirects = false
                ).headers
                val redirectlink = headerlocation.get("location")
                    .toString()
                if (redirectlink != "null") {
                    redirectlink
                } else {
                    link
                }
            }
            else -> link
        }
        val isM3u8 = false
        callback.invoke(
            ExtractorLink(
                name,
                name,
                lien,
                mainUrl,
                Qualities.Unknown.value,
                isM3u8 = isM3u8,
                headers = head
            )
        )
        return true
    }


    data class Cmds(
        @JsonProperty("ch_id") var chId: String? = null,
    )


    data class DataVoDnSeries(
        @JsonProperty("id") var id: String? = null,
        @JsonProperty("owner") var owner: String? = null,
        @JsonProperty("name") var name: String? = null,
        @JsonProperty("tmdb_id") var tmdbId: String? = null,
        @JsonProperty("old_name") var oldName: String? = null,
        @JsonProperty("o_name") var oName: String? = null,
        @JsonProperty("fname") var fname: String? = null,
        @JsonProperty("description") var description: String? = null,
        @JsonProperty("pic") var pic: String? = null,
        @JsonProperty("cost") var cost: Int? = null,
        //@JsonProperty("time") var time: Int? = null,
        @JsonProperty("file") var file: String? = null,
        @JsonProperty("path") var path: String? = null,
        @JsonProperty("protocol") var protocol: String? = null,
        @JsonProperty("rtsp_url") var rtspUrl: String? = null,
        @JsonProperty("censored") var censored: Int? = null,
        @JsonProperty("series") var series: ArrayList<String> = arrayListOf(),
        @JsonProperty("volume_correction") var volumeCorrection: Int? = null,
        @JsonProperty("category_id") var categoryId: String? = null,
        @JsonProperty("genre_id") var genreId: Int? = null,
        @JsonProperty("genre_id_1") var genreId1: Int? = null,
        @JsonProperty("genre_id_2") var genreId2: Int? = null,
        @JsonProperty("genre_id_3") var genreId3: Int? = null,
        @JsonProperty("hd") var hd: Int? = null,
        @JsonProperty("genre_id_4") var genreId4: Int? = null,
        @JsonProperty("cat_genre_id_1") var catGenreId1: String? = null,
        @JsonProperty("cat_genre_id_2") var catGenreId2: Int? = null,
        @JsonProperty("cat_genre_id_3") var catGenreId3: Int? = null,
        @JsonProperty("cat_genre_id_4") var catGenreId4: Int? = null,
        @JsonProperty("director") var director: String? = null,
        @JsonProperty("actors") var actors: String? = null,
        @JsonProperty("year") var year: String? = null,
        @JsonProperty("accessed") var accessed: Int? = null,
        @JsonProperty("status") var status: Int? = null,
        @JsonProperty("disable_for_hd_devices") var disableForHdDevices: Int? = null,
        @JsonProperty("added") var added: String? = null,
        @JsonProperty("count") var count: Int? = null,
        @JsonProperty("count_first_0_5") var countFirst05: Int? = null,
        @JsonProperty("count_second_0_5") var countSecond05: Int? = null,
        @JsonProperty("vote_sound_good") var voteSoundGood: Int? = null,
        @JsonProperty("vote_sound_bad") var voteSoundBad: Int? = null,
        @JsonProperty("vote_video_good") var voteVideoGood: Int? = null,
        @JsonProperty("vote_video_bad") var voteVideoBad: Int? = null,
        @JsonProperty("rate") var rate: String? = null,
        @JsonProperty("last_rate_update") var lastRateUpdate: String? = null,
        @JsonProperty("last_played") var lastPlayed: String? = null,
        @JsonProperty("for_sd_stb") var forSdStb: Int? = null,
        @JsonProperty("rating_im") var ratingIm: String? = null,
        @JsonProperty("rating_count_im") var ratingCountIm: String? = null,
        @JsonProperty("rating_last_update") var ratingLastUpdate: String? = null,
        @JsonProperty("age") var age: String? = null,
        @JsonProperty("high_quality") var highQuality: Int? = null,
        @JsonProperty("rating_kinopoisk") var ratingKinopoisk: String? = null,
        @JsonProperty("comments") var comments: String? = null,
        @JsonProperty("low_quality") var lowQuality: Int? = null,
        // @JsonProperty("is_series") var isSeries: Int? = null,
        @JsonProperty("year_end") var yearEnd: Int? = null,
        @JsonProperty("autocomplete_provider") var autocompleteProvider: String? = null,
        @JsonProperty("screenshots") var screenshots: String? = null,
        @JsonProperty("is_movie") var isMovie: Int? = null,
        @JsonProperty("lock") var lock: Int? = null,
        @JsonProperty("fav") var fav: Int? = null,
        @JsonProperty("for_rent") var forRent: Int? = null,
        @JsonProperty("screenshot_uri") var screenshotUri: String? = null,
        @JsonProperty("genres_str") var genresStr: String? = null,
        @JsonProperty("cmd") var cmd: String? = null,
        @JsonProperty("week_and_more") var weekAndMore: String? = null,
        @JsonProperty("has_files") var hasFiles: Int? = null
    )

    data class DataITV(
        @JsonProperty("id") var id: String? = null,
        @JsonProperty("name") var name: String? = null,
        @JsonProperty("number") var number: String? = null,
        @JsonProperty("tv_genre_id") var tvGenreId: String? = null,
        @JsonProperty("logo") var logo: String? = null,
        @JsonProperty("cmds") var cmds: ArrayList<Cmds> = arrayListOf(),
    )

    data class JsKey(
        @JsonProperty("token") var token: String? = null
    )

    data class Getkey(
        @JsonProperty("js") var js: JsKey? = JsKey()
    )

    data class JsInfo(
        @JsonProperty("mac") var mac: String? = null,
        @JsonProperty("phone") var phone: String? = null
    )

    data class GetExpiration(
        @JsonProperty("js") var js: JsInfo? = JsInfo()
    )

    data class JsITV(
        @JsonProperty("total_items") var totalItems: Int? = null,
        @JsonProperty("max_page_items") var maxPageItems: Int? = null,
        @JsonProperty("data") var data: ArrayList<DataITV> = arrayListOf()
    )

    data class JsVoDnSeries(
        @JsonProperty("total_items") var totalItems: Int? = null,
        @JsonProperty("max_page_items") var maxPageItems: Int? = null,
        @JsonProperty("data") var data: ArrayList<DataVoDnSeries> = arrayListOf()
    )

    data class JsonGetGenre(
        @JsonProperty("js") var js: ArrayList<Js_category> = arrayListOf()
    )

    data class Js_category(
        @JsonProperty("id") var id: String? = null,
        @JsonProperty("title") var title: String? = null,
    )

    data class RootITV(
        @JsonProperty("js") var js: JsITV? = JsITV()
    )

    data class RootVoDnSeries(
        @JsonProperty("js") var js: JsVoDnSeries? = JsVoDnSeries()
    )

    data class CategorieInfo(
        val title: String,
        val id: String,
        val type: String?,
        var dataStream: Channel? = null
    )

    fun AvoidProblem(): HomePageResponse {
        val helpCat =
            "❗️\uD83C\uDD7F\uD83C\uDD81\uD83C\uDD7E\uD83C\uDD71\uD83C\uDD7B\uD83C\uDD74\uD83C\uDD7C"

        val home = listOf(
            Pair(
                "Click to see the tips",
                listOf(
                    "There_is_an_error",
                    "https://bodhisattva4you.files.wordpress.com/2014/11/esprit-probleme-00.jpg",
                    "error"
                )
            ),
            Pair(
                "Find your stable \uD83D\uDC64 \uD83C\uDD78\uD83C\uDD7F\uD83C\uDD83\uD83C\uDD85 Account",
                listOf(
                    helpAcc,
                    "https://userguiding.com/wp-content/uploads/2021/06/best-help-center-software.jpg",
                    "account"
                )
            ),
        ).map {
            val title = it.first
            val url = it.second[0]
            val urlImage = it.second[1]
            val type = it.second[2]
            val catInfo = CategorieInfo(
                title,
                url,
                type,
            )
            TvSeriesSearchResponse(
                title,
                "${mainUrl}${catInfo.toJson()}",
                name,
                TvType.TvSeries,
                urlImage,
            )
        }
        return newHomePageResponse(helpCat, home)
    }

    /** Since I don't select all the content because it is too big, I want to at least display the countries and categories available.
     * So the user will know what is available and can select his own country or categories via the account creation tag */
    private fun getHelpHomePage(): HomePageList {
        val helpCat =
            "ℹ️\uD83C\uDDED\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEA\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF1\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF5\u200B\u200B\u200B\u200B\u200B ${name} (⏳ $expiration)"
        val home = listOf(
            Pair(
                "Find your stable \uD83D\uDC64 \uD83C\uDD78\uD83C\uDD7F\uD83C\uDD83\uD83C\uDD85 Account",
                listOf(
                    helpAcc,
                    "https://userguiding.com/wp-content/uploads/2021/06/best-help-center-software.jpg",
                    "account"
                )
            ),
            Pair(
                "\uD83D\uDD0E Your TAG Account (to filter)",
                listOf(
                    helpTag,
                    "https://ctcgulf.com/wp-content/uploads/2016/05/business9-600x400.jpg",
                    "tags"
                )
            )
        ).map {
            val title = it.first
            val url = it.second[0]
            val urlImage = it.second[1]
            val type = it.second[2]
            val catInfo = CategorieInfo(
                title,
                url,
                type,
            )
            TvSeriesSearchResponse(
                title,
                "${mainUrl}${catInfo.toJson()}",
                name,
                TvType.TvSeries,
                urlImage,
            )
        }
        return HomePageList(helpCat, home + helpSearch())
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (isNotInit) {
            getAuthHeader()
            expiration = tryParseJson<GetExpiration>(
                Regex("""\{[\s]*\"js\"(.*[\r\n]*)+\}""").find(
                    app.get(
                        "$mainUrl/portal.php?type=account_info&action=get_main_info&JsHttpRequest=1-xml&$defaulmacAdresse",
                        headers = headerMac
                    ).text
                )?.groupValues?.get(0)
            )?.js?.phone ?: "null"
        }
        val returnList = arrayListOf<HomePageList>()
        listOf("Live TV \uD83D\uDD34 code=0", "MOVIES code=1", "SERIES code=2").apmap {
            val categorie = it
            val type = when {
                categorie.contains("Live") -> "itv"
                categorie.contains("MOVIES") -> "vod"
                categorie.contains("SERIES") -> "series"
                else -> ""
            }
            val urlGetGenre = when (type) {
                "itv" -> "$mainUrl/portal.php?type=$type&action=get_genres"
                "vod" -> "$mainUrl/portal.php?type=$type&action=get_categories"
                "series" -> "$mainUrl/portal.php?type=$type&action=get_categories"
                else -> ""
            }

            val home = tryParseJson<JsonGetGenre>(
                Regex("""\{[\s]*\"js\"(.*[\r\n]*)+\}""").find(
                    app.get(
                        urlGetGenre,
                        headers = headerMac
                    ).text
                )?.groupValues?.get(0)
            )?.js?.getGenreNCategoriesInit(categorie)?.map {
                val idGenre = it.data
                val title = it.name
                val catInfo = CategorieInfo(
                    when (type) {
                        "itv" -> "0&$idGenre"
                        "vod" -> "1&$idGenre"
                        else -> "2&$idGenre"
                    },
                    idGenre,
                    type,
                )
                val titleN = "|$idGenre|${title.trim()}"
                when (type) {
                    "series" -> {
                        TvSeriesSearchResponse(
                            titleN,
                            "${mainUrl}${catInfo.toJson()}",
                            name,
                            TvType.TvSeries,
                            "https://static1.colliderimages.com/wordpress/wp-content/uploads/2020/10/best-tv-shows-to-binge-watch-social.png",
                        )
                    }
                    "vod" -> {
                        MovieSearchResponse(
                            titleN,
                            "${mainUrl}${catInfo.toJson()}",
                            name,
                            TvType.Live,
                            "https://nbcpalmsprings.com/wp-content/uploads/sites/8/2021/12/BEST-MOVIES-OF-2021.jpeg",
                        )
                    }
                    else -> {
                        LiveSearchResponse(
                            titleN,
                            "${mainUrl}${catInfo.toJson()}",
                            name,
                            TvType.Live,
                            "https://m.media-amazon.com/images/I/61hnxjB43nL.png",
                        )
                    }
                }
            }

            if (home != null) {
                if (home.isNotEmpty()) {
                    returnList.add(HomePageList(categorie, home))
                }
            }


        }
        if (returnList.isEmpty()) {
            return AvoidProblem()
        }
        return HomePageResponse(arrayListOf(getHelpHomePage()) + returnList.sortedBy {
            it.name.takeLast(
                1
            )
        })
    }

    companion object {
        var companionName: String? = null
        var loginMac: String? = null
        var overrideUrl: String? = null
        var tags: String? = null
        private var headerMac = mutableMapOf<String, String>()
        var expiration: String? = null
        fun findKeyWord(str: String): Regex {
            val upperSTR = str.uppercase()
            val sequence = when (upperSTR) {
                "EN" -> {
                    "US|UK|EN"
                }
                else -> upperSTR
            }
            return """(?:^|\W+|\s)+($sequence)(?:\s|\W+|${'$'}|\|)+""".toRegex()
        }

        fun cleanTitleButKeepNumber(title: String): String {
            return title.uppercase().replace("""FHD""", "")
                .replace(findKeyWord("VIP"), "")
                .replace("""UHD""", "").replace("""HEVC""", "")
                .replace("""HDR""", "").replace("""SD""", "").replace("""4K""", "")
                .replace("""HD""", "")
        }

        fun getFlag(sequence: String): String {
            val FR = findKeyWord("FR|FRANCE|FRENCH")
            val US = findKeyWord("US|USA")
            val AR = findKeyWord("AE|ARE|ARAB|ARABIC|ARABIA")
            val UK = findKeyWord("UK|EN|ENGLISH|ANGLAIS")
            val PR = findKeyWord("PT|PORTUGAL")
            val IN = findKeyWord("IN|INDIA|INDE|INDIAN")
            val IT = findKeyWord("IT|ITALIA|ITALIE|ITALIAN")
            val DE = findKeyWord("DE|GERMANY|ALLEMAGNE")
            val GR = findKeyWord("GR|GRECE|GREECE")
            val ES = findKeyWord("ES|SPAIN|SPANISH|ESPAGNE")
            val RU = findKeyWord("RU|RUSSIE|RUSSIA")
            return when {
                sequence.uppercase()
                    .contains(FR) -> sequence.replace(FR, "\uD83C\uDDE8\uD83C\uDDF5")
                sequence.uppercase()
                    .contains(US) -> sequence.replace(US, "\uD83C\uDDFA\uD83C\uDDF8")
                sequence.uppercase()
                    .contains(UK) -> sequence.replace(UK, "\uD83C\uDDEC\uD83C\uDDE7")
                sequence.uppercase()
                    .contains(PR) -> sequence.replace(PR, "\uD83C\uDDF5\uD83C\uDDF9")
                sequence.uppercase()
                    .contains(IN) -> sequence.replace(IN, "\uD83C\uDDEE\uD83C\uDDF3")
                sequence.uppercase()
                    .contains(IT) -> sequence.replace(IT, "\uD83C\uDDEE\uD83C\uDDF9")
                sequence.uppercase()
                    .contains(DE) -> sequence.replace(DE, "\uD83C\uDDE9\uD83C\uDDEA")
                sequence.uppercase()
                    .contains(GR) -> sequence.replace(GR, "\uD83C\uDDEC\uD83C\uDDF7")
                sequence.uppercase()
                    .contains(ES) -> sequence.replace(ES, "\uD83C\uDDEA\uD83C\uDDF8")
                sequence.uppercase()
                    .contains(AR) -> sequence.replace(AR, "\uD83C\uDDE6\uD83C\uDDEA")
                sequence.uppercase()
                    .contains(RU) -> sequence.replace(RU, "\uD83C\uDDF7\uD83C\uDDFA")
                else -> sequence
            }
        }

        fun cleanTitle(title: String): String {
            return cleanTitleButKeepNumber(title).replace(
                """(\s\d{1,}${'$'}|\s\d{1,}\s)""".toRegex(),
                " "
            )
        }

        fun ArrayList<DataITV>.sortByTitleNumber(): ArrayList<DataITV> {
            val regxNbr = Regex("""(\s\d{1,}${'$'}|\s\d{1,}\s)""")
            return ArrayList(this.sortedBy {
                val str = it.name.toString()
                regxNbr.find(str)?.groupValues?.get(0)?.trim()?.toInt() ?: 1000
            })
        }

    }
}

