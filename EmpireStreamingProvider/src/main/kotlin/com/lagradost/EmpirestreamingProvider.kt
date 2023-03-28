package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Element
import kotlin.collections.ArrayList
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.nicehttp.JsonAsString
import com.lagradost.nicehttp.NiceResponse
import kotlinx.coroutines.runBlocking


class EmpirestreamingProvider : MainAPI() {


    override var mainUrl = "https://empire-streaming.eu"
    override var name = "\uD83D\uDC51 Empire-Streaming \uD83D\uDC51"
    override val hasQuickSearch = false // recherche rapide (optionel, pas vraimet utile)
    override val hasMainPage = true // page d'accueil (optionel mais encoragé)
    override var lang = "fr" // fournisseur est en francais
    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries) // series, films
    private val interceptor = CloudflareKiller()

    init {
        runBlocking {
            try {
                app.get(mainUrl)
            } catch (e: Exception) { // url changed
                val data =
                    tryParseJson<ArrayList<mediaData>>(app.get("https://raw.githubusercontent.com/Eddy976/cloudstream-extensions-eddy/ressources/fetchwebsite.json").text)!!
                data.forEach {
                    if (it.title.lowercase().contains("Empire")) {
                        mainUrl = it.url
                    }
                }

            }
        }
    }


    data class SearchJson(

        @JsonProperty("status") var status: Boolean? = null,
        @JsonProperty("data") var data: Data? = Data()

    )


    data class Films(

        @JsonProperty("id") var id: Int? = null,
        @JsonProperty("title") var title: String? = null,
        @JsonProperty("versions") var versions: ArrayList<String> = arrayListOf(),
        @JsonProperty("dateCreatedAt") var dateCreatedAt: String? = null,
        @JsonProperty("description") var description: String? = null,
        @JsonProperty("label") var label: String? = null,
        @JsonProperty("image") var image: ArrayList<Image> = arrayListOf(),
        @JsonProperty("season") var season: String? = null,
        @JsonProperty("new_episode") var newEpisode: NewEpisode? = NewEpisode(),
        @JsonProperty("sym_image") var symImage: SymImage? = SymImage(),
        @JsonProperty("BackDrop") var BackDrop: ArrayList<BackDrop> = arrayListOf(),
        @JsonProperty("note") var note: Int? = null,
        @JsonProperty("createdAt") var createdAt: String? = null,
        @JsonProperty("path") var path: String? = null,
        @JsonProperty("trailer") var trailer: String? = null,
        @JsonProperty("urlPath") var urlPath: String? = null

    )

    data class Data(

        @JsonProperty("films") var films: ArrayList<Films> = arrayListOf(),
        @JsonProperty("series") var series: ArrayList<Series> = arrayListOf()

    )

    data class Series(

        @JsonProperty("id") var id: Int? = null,
        @JsonProperty("title") var title: String? = null,
        @JsonProperty("versions") var versions: ArrayList<String> = arrayListOf(),
        @JsonProperty("dateCreatedAt") var dateCreatedAt: String? = null,
        @JsonProperty("description") var description: String? = null,
        @JsonProperty("label") var label: String? = null,
        @JsonProperty("image") var image: ArrayList<Image> = arrayListOf(),
        @JsonProperty("season") var season: String? = null,
        @JsonProperty("new_episode") var newEpisode: NewEpisode? = NewEpisode(),
        @JsonProperty("sym_image") var symImage: SymImage? = SymImage(),
        @JsonProperty("BackDrop") var BackDrop: ArrayList<BackDrop> = arrayListOf(),
        @JsonProperty("note") var note: Int? = null,
        @JsonProperty("createdAt") var createdAt: String? = null,
        @JsonProperty("path") var path: String? = null,
        @JsonProperty("trailer") var trailer: String? = null,
        @JsonProperty("urlPath") var urlPath: String? = null

    )

    /**
    Cherche le site pour un titre spécifique

    La recherche retourne une SearchResponse, qui peut être des classes suivants: AnimeSearchResponse, MovieSearchResponse, TorrentSearchResponse, TvSeriesSearchResponse
    Chaque classes nécessite des données différentes, mais a en commun le nom, le poster et l'url
     **/
    override suspend fun search(query: String): List<SearchResponse> {
        val json = JsonAsString("""{"search":"$query"}""")
        println(json)
        val html =
            app.post(
                "$mainUrl/api/views/search",
                data = null,
                json = json, interceptor = interceptor
            )
        val jsonrep = html.parsed<SearchJson>()
        println(jsonrep)
        return jsonrep.data!!.series.map {
            newAnimeSearchResponse(
                name = it.title.toString(),
                url = fixUrl(it.urlPath.toString()),
                type = TvType.TvSeries,

                ) {
                this.posterUrl = fixUrl("/images/medias" + it.symImage!!.poster.toString())
                this.posterHeaders =
                    interceptor.getCookieHeaders("$mainUrl/api/views/search").toMap()
                addDubStatus(
                    isDub = it.versions.any { it.contains("vf") },
                    episodes = null
                )
            }
        } + jsonrep.data!!.films.map {
            newAnimeSearchResponse(
                name = it.title.toString(),
                url = fixUrl(it.urlPath.toString()),
                type = TvType.TvSeries,

                ) {
                this.posterUrl =
                    fixUrl("/images/medias" + it.symImage!!.poster.toString())
                this.posterHeaders =
                    interceptor.getCookieHeaders("$mainUrl/api/views/search").toMap()
                addDubStatus(
                    isDub = it.versions.any { it.contains("vf") },
                    episodes = null
                )
            }
        }

    }

    /**
     * charge la page d'informations, il ya toutes les donées, les épisodes, le résumé etc ...
     * Il faut retourner soit: AnimeLoadResponse, MovieLoadResponse, TorrentLoadResponse, TvSeriesLoadResponse.
     */


    data class EpisodeInfo(

        @JsonProperty("id") var id: Int? = null,
        @JsonProperty("versions") var versions: ArrayList<String> = arrayListOf(),
        @JsonProperty("createdAt") var createdAt: CreatedAt? = CreatedAt(),
        @JsonProperty("title") var title: String? = null,
        @JsonProperty("description") var description: String? = null,
        @JsonProperty("episode") var episode: Int? = null,
        @JsonProperty("saison") var saison: Int? = null,
        @JsonProperty("YearProduct") var YearProduct: YearProduct? = YearProduct(),
        @JsonProperty("label") var label: String? = null,
        @JsonProperty("sym_image") var symImage: String? = null,
        @JsonProperty("video") var video: ArrayList<Video> = arrayListOf(),
        @JsonProperty("image") var image: ArrayList<Image> = arrayListOf()

    )

    data class CreatedAt(

        @JsonProperty("date") var date: String? = null,
        @JsonProperty("timezone_type") var timezoneType: Int? = null,
        @JsonProperty("timezone") var timezone: String? = null

    )

    data class YearProduct(

        @JsonProperty("date") var date: String? = null,
        @JsonProperty("timezone_type") var timezoneType: Int? = null,
        @JsonProperty("timezone") var timezone: String? = null

    )

    data class Video(

        @JsonProperty("id") var id: Int? = null,
        @JsonProperty("code") var code: String? = null,
        @JsonProperty("property") var property: String? = null,
        @JsonProperty("version") var version: String? = null,
        @JsonProperty("title") var title: String? = null,
        @JsonProperty("editMod") var editMod: Boolean? = null

    )

    data class Image(

        @JsonProperty("path") var path: String? = null,
        @JsonProperty("property") var property: String? = null,
        @JsonProperty("size") var size: String? = null

    )

    data class GetDistrion(

        @JsonProperty("Distribution") var Distribution: ArrayList<Distribution> = arrayListOf()

    )

    fun findVideolink(proprety: String?, code: String?): String {
        return when (proprety) {
            "voe" -> {
                "https://voe.sx/e/$code"
            }

            "streamsb" -> {
                "https://playersb.com/e/$code"
            }
            "doodstream" -> {
                "https://dood.pm/e/$code"
            }
            else -> code.toString()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val html =
            avoidCloudflare(url) //
        val document = html.document
        // url est le lien retourné par la fonction search (la variable href) ou la fonction getMainPage
        val subEpisodes = ArrayList<Episode>()
        val dubEpisodes = ArrayList<Episode>()
        val distribution =
            tryParseJson<GetDistrion>(
                "{${
                    Regex("""("Distribution\"\:[\s]*\[\{.*\}\]),[\s]*"Category""").find(html.text)?.groupValues?.get(
                        1
                    ) ?: ""
                }}"
            )?.Distribution?.map {
                ActorData(
                    Actor(
                        it.name.toString(),
                        fixUrl("/images/distributions" + it.image[0].path.toString())
                    )
                )
            }

        val jsonText =
            Regex("""result[\s]+=([\s]+.*\}\]\})[\s]*;""").find(html.text)!!.groupValues.get(1)
        var dataUrl = url
        if (document.select("article > div > span.ff-fb").text().contains("serie", true)) {
            Regex("""(\[\{"id".*?\}\]\}\])""").findAll(jsonText).toList().apmap { season ->
                Regex("""(\{"id".*?\}\]\})""").findAll(season.groupValues.get(0))
                    .forEach { ep ->
                        val episodeJson = tryParseJson<EpisodeInfo>(ep.groupValues.get(0))!!
                        var addVidVF = ""
                        var addVid = ""
                        episodeJson.video.forEach { vid ->
                            if (vid.version == "vf") {
                                addVidVF =
                                    "$addVidVF&${findVideolink(vid.property, vid.code)}"
                                dubEpisodes.add(
                                    Episode(
                                        data = addVidVF,
                                        name = "\uD83C\uDDE8\uD83C\uDDF5 " + episodeJson.title,
                                        season = episodeJson.saison,
                                        episode = episodeJson.episode,
                                        posterUrl = fixUrl("/images/episodes" + episodeJson.symImage.toString()),
                                        description = episodeJson.description
                                    )
                                )
                            } else {
                                addVid = "$addVid&${findVideolink(vid.property, vid.code)}"
                                subEpisodes.add(
                                    Episode(
                                        data = addVid,
                                        name = episodeJson.title,
                                        season = episodeJson.saison,
                                        episode = episodeJson.episode,
                                        posterUrl = fixUrl("/images/episodes" + episodeJson.symImage.toString()),
                                        description = episodeJson.description
                                    )
                                )
                            }
                        }
                    }
            }


        } else {
            val data = tryParseJson<MovieJson>(jsonText)!!
            var addVidVF = ""
            var addVid = ""
            data.Iframe.forEach { vid ->

                if (vid.version == "vf") {
                    addVidVF = "$addVidVF&${findVideolink(vid.property, vid.code)}*vf"
                } else {
                    addVid = "$addVid&${findVideolink(vid.property, vid.code)}*vostfr"
                }
            }
            dataUrl = "$addVidVF||$addVid"
        }

        val title =
            document.select("h1.fs-40.c-w.ff-bb.tt-u.mb-0.ta-md-c.fs-md-30.mb-2").text()
        val posterUrl =
            fixUrl(document.select("picture > img").attr("data-src"))
        val year = document.select("span.c-w.ff-cond.ml-2.ml-md-0.mt-md-1").text().toIntOrNull()


        val tags = document.select("ul.d-f.f-w.ls-n.mb-2.jc-md-c > li")
            .map { it.text() } // séléctione tous les tags et les ajoutes à une liste
        ///////////////////////////////////////////
        ///////////////////////////////////////////

        var type_rec: TvType

        val recommendations = document.select("ul.block-suggest>li").map { element ->
            val recTitle =
                element.select("div > section").attr("data-title")
            val image = fixUrl(element.select("div > picture >img").attr("data-src"))
            val recUrl = fixUrl(element.select("div > section").attr("data-urlpath"))
            type_rec = TvType.TvSeries
            if (element.attr("data-itype").contains("film", true)) type_rec =
                TvType.Movie

            if (type_rec == TvType.TvSeries) {
                TvSeriesSearchResponse(
                    recTitle,
                    recUrl,
                    this.name,
                    TvType.TvSeries,
                    image,

                    )
            } else
                MovieSearchResponse(
                    recTitle,
                    recUrl,
                    this.name,
                    TvType.Movie,
                    image,
                )

        }

        if (subEpisodes.isEmpty() && dubEpisodes.isEmpty()) {

            return newMovieLoadResponse(
                name = title,
                url = url,
                type = TvType.Movie,
                dataUrl = dataUrl

            ) {
                this.posterUrl = fixUrl(posterUrl)
                this.plot = document.select("p.description").text()
                this.year = year
                this.tags = tags
                this.posterHeaders = interceptor.getCookieHeaders(url).toMap()
                this.recommendations = recommendations
                this.actors = distribution
                addTrailer(
                    "https://www.youtube.com/watch?v=" + document.select("button.action-see-more")
                        .attr("data-trailer")
                )
            }
        } else {
            return newAnimeLoadResponse(
                title,
                url,
                TvType.Anime,
            ) {
                this.posterUrl = fixUrl(posterUrl)
                this.plot = document.select("p.description").text()
                this.recommendations = recommendations
                this.year = year
                this.tags = tags
                this.posterHeaders = interceptor.getCookieHeaders(url).toMap()
                this.actors = distribution
                addTrailer(
                    "https://www.youtube.com/watch?v=" + document.select("button.action-see-more")
                        .attr("data-trailer")
                )
                if (subEpisodes.isNotEmpty()) addEpisodes(
                    DubStatus.Subbed,
                    subEpisodes
                )
                if (dubEpisodes.isNotEmpty()) addEpisodes(
                    DubStatus.Dubbed,
                    dubEpisodes
                )
            }
        }
    }


    // récupere les liens .mp4 ou m3u8 directement à partir du paramètre data généré avec la fonction load()
    override suspend fun loadLinks(
        data: String, // fournit par load()
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        data.split("||").forEach {
            it.split("&").forEach {
                var playerUrl = it
                val flag = if (playerUrl.contains("*vf")) {
                    playerUrl = playerUrl.replace("*vf", "")
                    "\uD83C\uDDE8\uD83C\uDDF5"
                } else if (playerUrl.contains("*vostfr")) {
                    playerUrl = playerUrl.replace("*vostfr", "")
                    "\uD83C\uDDEC\uD83C\uDDE7"
                } else {
                    ""
                }
                if (!playerUrl.isBlank()) {
                    loadExtractor(
                        httpsify(playerUrl),
                        mainUrl,
                        subtitleCallback
                    ) { link ->
                        callback.invoke(
                            ExtractorLink(
                                link.source,
                                link.name + flag,
                                link.url,
                                link.referer,
                                Qualities.Unknown.value,
                                link.isM3u8,
                                link.headers,
                                link.extractorData
                            )
                        )
                    }
                }
            }
        }

        return true
    }

    private fun Element.toSearchResponse(url: String): SearchResponse {

        val posterUrl = fixUrl(select("div.w-100 > picture > img").attr("data-src"))
        val type = select("div.w-100 > a").attr("data-itype")
        val title = select("div.w-100 > section").attr("data-title")
        val link = fixUrl(select("div.w-100 > a").attr("href"))
        if (type.contains("film", true)) {
            return MovieSearchResponse(
                name = title,
                url = link,
                apiName = title,
                type = TvType.Movie,
                posterUrl = posterUrl,
                posterHeaders = interceptor.getCookieHeaders(url).toMap()
            )


        } else  // an Serie
        {
            return newAnimeSearchResponse(
                name = title,
                url = link,
                type = TvType.TvSeries,

                ) {
                this.posterUrl = posterUrl
                this.posterHeaders = interceptor.getCookieHeaders(url).toMap()
                addDubStatus(
                    isDub = select(" div.w-100 > picture > img").attr("alt")
                        .contains("vf", true),
                    episodes = null
                )
            }

        }
    }

    data class MovieJson(

        @JsonProperty("Iframe") var Iframe: ArrayList<Iframe> = arrayListOf(),
        @JsonProperty("Distribution") var Distribution: ArrayList<Distribution> = arrayListOf(),
    )


    data class NewEpisode(

        @JsonProperty("bool") var bool: Boolean? = null,
        @JsonProperty("details") var details: String? = null

    )

    data class SymImage(

        @JsonProperty("poster") var poster: String? = null,
        @JsonProperty("backdrop") var backdrop: String? = null
    )

    data class BackDrop(

        @JsonProperty("path") var path: String? = null,
        @JsonProperty("property") var property: String? = null,
        @JsonProperty("size") var size: String? = null

    )


    data class Poster(

        @JsonProperty("path") var path: String? = null,
        @JsonProperty("property") var property: String? = null,
        @JsonProperty("size") var size: String? = null

    )


    data class Iframe(

        @JsonProperty("id") var id: Int? = null,
        @JsonProperty("code") var code: String? = null,
        @JsonProperty("property") var property: String? = null,
        @JsonProperty("version") var version: String? = null,
        @JsonProperty("title") var title: String? = null,
        @JsonProperty("editMod") var editMod: Boolean? = null

    )


    data class Distribution(

        @JsonProperty("id") var id: Int? = null,
        @JsonProperty("name") var name: String? = null,
        @JsonProperty("image") var image: ArrayList<Image> = arrayListOf()

    )

    data class Date(

        @JsonProperty("date") var date: String? = null,
        @JsonProperty("timezone_type") var timezoneType: Int? = null,
        @JsonProperty("timezone") var timezone: String? = null

    )

    suspend fun avoidCloudflare(url: String): NiceResponse {
        return app.get(url, interceptor = interceptor)
    }

    data class mediaData(
        @JsonProperty("title") var title: String,
        @JsonProperty("url") val url: String,
    )


    override val mainPage = mainPageOf(
        Pair(
            "$mainUrl/univer/netflix-en-streaming-hd/8",
            "\uD83C\uDDF3\u200C\uD83C\uDDEA\u200C\uD83C\uDDF9\u200C\uD83C\uDDEB\u200C\uD83C\uDDF1\u200C\uD83C\uDDEE\u200C\uD83C\uDDFD\u200C"

        ),
        Pair(
            "$mainUrl/univer/Walt-Disney-Pictures-en-streaming-hd/12",
            "\uD83C\uDDE9\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEE\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF8\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF3\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEA\u200B\u200B\u200B\u200B\u200B\uD83C\uDDFE\u200B\u200B\u200B\u200B\u200B +"
        ),
        Pair(
            "$mainUrl/univer/Amazon-prime-video-en-streaming-hd/10",
            "ⓐⓜⓐⓩⓞⓝ \uD83C\uDDF5\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF7\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEE\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF2\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEA\u200B\u200B\u200B\u200B\u200B"
        ),
        Pair(
            "$mainUrl/univer/Apple-Tv-en-streaming-hd/15",
            "\uD83C\uDDE6\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF5\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF5\u200B\u200B\u200B\u200B\u200B\uD83C\uDDF1\u200B\u200B\u200B\u200B\u200B\uD83C\uDDEA\u200B\u200B\u200B\u200B\u200B \uD83D\uDCFA"

        ),
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + "?page=$page&filter=dateCreatedAt&video=films"
        val document =
            avoidCloudflare(url).document
        val movies = document.select("li.card-web.card-video")

        val home =
            movies.map { article ->  // avec mapnotnull si un élément est null, il sera automatiquement enlevé de la liste
                article.toSearchResponse(url)
            }
        return newHomePageResponse(request.name, home)
    }

}





