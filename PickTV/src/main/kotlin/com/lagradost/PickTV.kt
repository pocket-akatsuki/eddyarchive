package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.runBlocking
import me.xdrop.fuzzywuzzy.FuzzySearch
import okhttp3.Interceptor

class PickTV : MainAPI() {
    override var mainUrl = "https://github.com"
    val urlmain =
        "https://raw.githubusercontent.com/Eddy976/cloudstream-extensions-eddy/ressources/trickylist.json"
    override var name = "MyPickTV"
    override val hasQuickSearch = false // recherche rapide (optionel, pas vraimet utile)
    override val hasMainPage = true // page d'accueil (optionel mais encoragé)
    override var lang = "fr" // fournisseur est en francais
    override val supportedTypes =
        setOf(TvType.Live) // live
    //val takeN = 10

    /**
    Cherche le site pour un titre spécifique

    La recherche retourne une SearchResponse, qui peut être des classes suivants: AnimeSearchResponse, MovieSearchResponse, TorrentSearchResponse, TvSeriesSearchResponse
    Chaque classes nécessite des données différentes, mais a en commun le nom, le poster et l'url
     **/

    private fun List<mediaData>.sortByname(query: String?): List<mediaData> {
        return if (query == null) {
            // Return list to base state if no query
            this.sortedBy { it.title }
        } else {

            this.sortedBy {
                val name = cleanTitle(it.title)
                -FuzzySearch.ratio(name.lowercase(), query.lowercase())
            }
        }
    }

    private fun List<SearchResponse>.sortBynameNumber(): List<SearchResponse> {
        val regxNbr = Regex("""(\s\d{1,}${'$'}|\s\d{1,}\s)""")
        return this.sortedBy {
            val str = it.name
            regxNbr.find(str)?.groupValues?.get(0)?.trim()?.toInt() ?: -10
        }
    }

    /**
    Cherche le site pour un titre spécifique

    La recherche retourne une SearchResponse, qui peut être des classes suivants: AnimeSearchResponse, MovieSearchResponse, TorrentSearchResponse, TvSeriesSearchResponse
    Chaque classes nécessite des données différentes, mais a en commun le nom, le poster et l'url
     **/
    private val resultsSearchNbr = 50
    override suspend fun search(query: String): List<SearchResponse> {
        val searchResutls = ArrayList<SearchResponse>()
        val reponse = app.get(urlmain).text
        val arraymediaPlaylist = tryParseJson<ArrayList<mediaData>>(reponse)!!
        var poster: String?
        arraymediaPlaylist.sortByname(query).take(resultsSearchNbr).forEach { media ->
            poster =
                if (media.url_groupPoster?.isBlank() == false && media.url_image?.isBlank() == true) {
                    media.url_groupPoster
                } else {
                    media.url_image
                }
            searchResutls.add(
                LiveSearchResponse(
                    "${getFlag(media.lang.toString())} ${media.title}",
                    media.url,
                    media.title,
                    TvType.Live,
                    poster.toString(),
                )
            )
        }

        return searchResutls

    }

    /**
     * charge la page d'informations, il ya toutes les donées, les épisodes, le résumé etc ...
     * Il faut retourner soit: AnimeLoadResponse, MovieLoadResponse, TorrentLoadResponse, TvSeriesLoadResponse.
     */
    override suspend fun load(url: String): LoadResponse {
        val allresultshome = arrayListOf<SearchResponse>()

        var link = ""
        var title = ""
        var posterUrl = ""
        var posterurlRec: String?
        var flag = ""
        val arraymediaPlaylist = tryParseJson<ArrayList<mediaData>>(app.get(urlmain).text)!!
        for (media in arraymediaPlaylist) {
            if (url.replace(mainUrl, "") == media.url) {
                link = media.url
                title = media.title
                flag = getFlag(media.lang.toString())
                val a = rgxSelectFirstWord.find(cleanTitle(title))!!.groupValues[0]
                posterUrl =
                    if (media.url_groupPoster?.isBlank() == false && media.url_image?.isBlank() == true) {
                        media.url_groupPoster
                    } else {
                        media.url_image
                    }.toString()
                var b_new: String
                arraymediaPlaylist.forEach { channel ->
                    val b = cleanTitle(channel.title)
                    b_new = rgxSelectFirstWord.find(b)!!.groupValues[0]//b.take(takeN)
                    if (a == b_new && media.genre == channel.genre && media.url != channel.url
                    ) {
                        val streamurl = channel.url
                        val channelname = channel.title

                        val nameURLserver = "\uD83D\uDCF6" + streamurl.replace("http://", "")
                            .replace("https://", "").take(8)
                        val uppername = channelname.uppercase()
                        val quality = getQualityFromString(
                            when (!channelname.isBlank()) {
                                uppername.contains(findCountryId("UHD")) -> {
                                    "UHD"
                                }
                                uppername.contains(findCountryId("HD")) -> {
                                    "HD"
                                }
                                uppername.contains(findCountryId("SD")) -> {
                                    "SD"
                                }
                                uppername.contains(findCountryId("FHD")) -> {
                                    "HD"
                                }
                                uppername.contains(findCountryId("4K")) -> {
                                    "FourK"
                                }

                                else -> {
                                    null
                                }
                            }
                        )

                        posterurlRec =
                            if (media.url_groupPoster?.isBlank() == false && channel.url_image?.isBlank() == true) {
                                media.url_groupPoster
                            } else {
                                channel.url_image
                            }
                        allresultshome.add(
                            LiveSearchResponse(
                                name = "${cleanTitleKeepNumber(channelname)} $nameURLserver",
                                url = streamurl,
                                name,
                                TvType.Live,
                                posterUrl = posterurlRec,
                                quality = quality,
                                lang = channel.lang,
                            )
                        )

                    }

                }
                break
            }
        }
        val nameURLserver =
            "\uD83D\uDCF6" + link.replace("http://", "").replace("https://", "").take(8)

        val recommendation = if (allresultshome.isNotEmpty()) {
            allresultshome.sortBynameNumber()
        } else {
            null
        }
        return LiveStreamLoadResponse(
            name = "$title $flag $nameURLserver",
            url = link,
            apiName = this.name,
            dataUrl = link,
            posterUrl = posterUrl,
            recommendations = recommendation
        )
    }


    /**
     * Some providers ask for new token so we intercept the request to change the token
     * */
    suspend fun findOriginlink(str: String): String? {
        val reponse = app.get(urlmain).text
        val arraymediaPlaylist = tryParseJson<ArrayList<mediaData>>(reponse)!!
        for (media in arraymediaPlaylist) {
            if (media.url.contains(str) && media.url.takeLast(1) == str.takeLast(1)) {
                return media.url
            }
        }
        return null
    }

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        // Needs to be object instead of lambda to make it compile correctly
        return object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
                val request = chain.request()
                if (request.url.toString().contains("token")) {
                    val str =
                        request.url.toString().replace("""http[s]?\:\/\/[^\/]*""".toRegex(), "")
                            .replace("""\?token.*""".toRegex(), "")
                    var link: String
                    runBlocking {
                        link = findOriginlink(str) ?: request.url.toString()
                        when (true) {
                            link.contains("dreamsat.ddns") -> {
                                val headers1 = mapOf(
                                    "User-Agent" to "REDLINECLIENT_DREAMSAT_650HD_PRO_RICHTV_V02",
                                    "Accept-Encoding" to "identity",
                                    "Connection" to "Keep-Alive",
                                )
                                val headerlocation = app.get(
                                    link, headers = headers1,
                                    allowRedirects = false
                                ).headers
                                val redirectlink = headerlocation.get("location")
                                    .toString()

                                if (redirectlink != "null") {
                                    link = redirectlink
                                }
                            }
                            else -> {
                            }
                        }
                    }
                    val newRequest = chain.request()
                        .newBuilder().url(link).build()
                    return chain.proceed(newRequest)
                } else {
                    return chain.proceed(chain.request())
                }
            }
        }
    }

    val rgxGetUrlRef = Regex("""(http[s:\/\/]*[^\/]*)""")

    /** récupere les liens .mp4 ou m3u8 directement à partir du paramètre data généré avec la fonction load()**/
    override suspend fun loadLinks(
        data: String, // fournit par load()
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        var isM3u = false
        var link: String = data
        val invokeHeader = mapOf<String, String>()
        var refer: String = ""
        when (true) {
            (data.contains("m3u") && data.contains("webudi.openhd")) -> {
                isM3u = true
                refer = "https://streamservicehd.click/"
            }

            data.contains("dreamsat.ddns") -> {
                val headers1 = mapOf(
                    "User-Agent" to "REDLINECLIENT_DREAMSAT_650HD_PRO_RICHTV_V02",
                    "Accept-Encoding" to "identity",
                    "Connection" to "Keep-Alive",
                )
                val headerlocation = app.get(
                    data, headers = headers1,
                    allowRedirects = false
                ).headers
                val redirectlink = headerlocation.get("location")
                    .toString()

                if (redirectlink != "null") {
                    link = redirectlink
                }
                refer = "${rgxGetUrlRef.find(link)?.groupValues?.get(0).toString()}/"

            }
            else -> {
                link = data
                refer = "${rgxGetUrlRef.find(link)?.groupValues?.get(0).toString()}/"

            }
        }

        val live = link.replace("http://", "").replace("https://", "").take(8) + " \uD83D\uDD34"

        callback.invoke(
            ExtractorLink(
                name,
                live,
                link,
                refer,
                Qualities.Unknown.value,
                isM3u8 = isM3u,
                headers = invokeHeader,

                )
        )
        return true
    }

    data class mediaData(
        @JsonProperty("title") var title: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("genre") val genre: String?,
        @JsonProperty("url_image") val url_image: String?,
        @JsonProperty("lang") val lang: String?,
        @JsonProperty("url_groupPoster") var url_groupPoster: String?,
    )

    private fun findCountryId(codeCountry: String): Regex {
        return """(?:^|\W+|\s)+($codeCountry)(?:\s|\W+|${'$'}|\|)+""".toRegex()
    }

    private fun getFlag(sequence: String): String {
        val FR = findCountryId("FR|FRANCE|FRENCH")
        val US = findCountryId("US|USA")
        val AR = findCountryId("AR|ARAB|ARABIC")
        val UK = findCountryId("UK")
        val flag: String
        flag = when (true) {
            sequence.uppercase()
                .contains(FR) -> "\uD83C\uDDE8\uD83C\uDDF5"
            sequence.uppercase()
                .contains(US) -> "\uD83C\uDDFA\uD83C\uDDF8"
            sequence.uppercase()
                .contains(UK) -> "\uD83C\uDDEC\uD83C\uDDE7"
            sequence.uppercase()
                .contains(AR) -> " نظرة"
            else -> ""
        }
        return flag
    }

    private fun getGenreIcone(sequence: String): String {
        val SPORT = findCountryId("SPORT")
        val INFO = findCountryId("INFO")
        val GENERAL = findCountryId("GENERAL")
        val MUSIQUE = findCountryId("MUSIQUE")
        val CINEMA = findCountryId("CINEMA")
        val SERIES = findCountryId("SERIE")
        val DIVERTISSEMENT = findCountryId("DIVERTISSEMENT")
        val JEUNESSE = findCountryId("JEUNESSE")
        val DOCUMENTAIRE = findCountryId("DOCUMENTAIRE")
        val genreIcon: String
        genreIcon = when (true) {
            sequence.uppercase()
                .contains(SPORT) -> "⚽ \uD83E\uDD4A \uD83C\uDFC0 \uD83C\uDFBE"
            sequence.uppercase()
                .contains(INFO) -> "\uD83E\uDD25 \uD83D\uDCF0 ⚠ Peu importe la source toujours vérifier les infos"
            sequence.uppercase()
                .contains(CINEMA) -> "\uD83C\uDFA5"
            sequence.uppercase()
                .contains(MUSIQUE) -> "\uD83C\uDFB6"
            sequence.uppercase()
                .contains(SERIES) -> "\uD83D\uDCFA"
            sequence.uppercase()
                .contains(DIVERTISSEMENT) -> "✨"
            sequence.uppercase()
                .contains(JEUNESSE) -> "\uD83D\uDC67\uD83D\uDC75\uD83D\uDE1B"
            sequence.uppercase().contains(DOCUMENTAIRE) -> "\uD83E\uDD96"
            sequence.uppercase().contains(GENERAL) -> "\uD83E\uDDD0 \uD83D\uDCFA"
            else -> ""
        }
        return genreIcon
    }

    private fun ArrayList<mediaData>.sortByTitleNumber(): ArrayList<mediaData> {
        val regxNbr = Regex("""(\s\d{1,}${'$'}|\s\d{1,}\s)""")
        return ArrayList(this.sortedBy {
            val str = it.title
            regxNbr.find(str)?.groupValues?.get(0)?.trim()?.toInt() ?: 1000
        })
    }

    val rgxSelectFirstWord = Regex("""(^[^\s]*)""")
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val arrayHomepage = arrayListOf<HomePageList>()
        val arraymediaPlaylist = tryParseJson<ArrayList<mediaData>>(app.get(urlmain).text)!!
        val genreMedia = ArrayList<String>()
        var newGenre: String
        var category: String
        var newgenreMedia: Boolean
        var posterUrl: String?
        ///////////////////////
        arraymediaPlaylist.forEach { mediaGenre ->
            newGenre = cleanTitle(mediaGenre.genre.toString())//

            newgenreMedia = true
            for (nameGenre in genreMedia) {
                if (nameGenre.contains(newGenre)) {
                    newgenreMedia = false
                    break
                }
            }
            if (newgenreMedia
            ) {
                genreMedia.add(newGenre)
                category = newGenre
                val groupMedia = ArrayList<String>()
                var b_new: String
                var newgroupMedia: Boolean
                var mediaGenre: String
                val home = arraymediaPlaylist.sortByTitleNumber().mapNotNull { media ->

                    val b = cleanTitle(media.title)//
                    b_new = rgxSelectFirstWord.find(b)!!.groupValues[0]  //b.take(takeN)
                    newgroupMedia = true
                    mediaGenre = cleanTitle(media.genre.toString())
                    for (nameMedia in groupMedia) {
                        if (nameMedia == b_new && (mediaGenre == category)) {
                            newgroupMedia = false
                            break
                        }
                    }
                    if (newgroupMedia && (mediaGenre == category)
                    ) {

                        groupMedia.add(b_new)
                        val groupName = b_new//"${cleanTitle(media.title)}"

                        posterUrl =
                            if (media.url_groupPoster?.isBlank() == true && media.url_image?.isBlank() == false) {
                                media.url_image
                            } else {
                                media.url_groupPoster
                            }
                        LiveSearchResponse(
                            groupName,
                            media.url + mainUrl,
                            name,
                            TvType.Live,
                            posterUrl,
                        )
                    } else {
                        null
                    }

                }
                arrayHomepage.add(
                    HomePageList(
                        "$category ${getGenreIcone(category)}",
                        home,
                        isHorizontalImages = true
                    )
                )
            }
        }

        return HomePageResponse(
            arrayHomepage
        )
    }

    private fun cleanTitle(title: String): String {
        return title.uppercase().replace("""(\s\d{1,}${'$'}|\s\d{1,}\s)""".toRegex(), " ")
            .replace("""FHD""", "")
            .replace("""VIP""", "")
            .replace("""UHD""", "").replace("""HEVC""", "")
            .replace("""HDR""", "").replace("""SD""", "").replace("""4K""", "")
            .replace("""HD""", "").replace(findCountryId("FR|AF"), "").trim()
    }

    private fun cleanTitleKeepNumber(title: String): String {
        return title.uppercase().replace("""FHD""", "")
            .replace("""VIP""", "")
            .replace("""UHD""", "").replace("""HEVC""", "")
            .replace("""HDR""", "").replace("""SD""", "").replace("""4K""", "")
            .replace("""HD""", "").replace(findCountryId("FR|AF"), "").trim()
    }
}


