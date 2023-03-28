package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import kotlin.collections.ArrayList
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.nicehttp.NiceResponse
import java.util.*


class WiflixProvider : MainAPI() {
    override var mainUrl = "https://wiflix.cafe"
    override var name = "Wiflix"
    override val hasQuickSearch = false // recherche rapide (optionel, pas vraimet utile)
    override val hasMainPage = true // page d'accueil (optionel mais encoragé)
    override var lang = "fr" // fournisseur est en francais
    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries) // series, films
    private val interceptor = CloudflareKiller()
    private var isNotInit = true

    suspend fun initMainUrl() {
        try {
            val document = avoidCloudflare(mainUrl).document
            val newMainUrl = document.select("link[rel*=\"canonical\"]").attr("href")
            if (!newMainUrl.isNullOrBlank() && newMainUrl.contains("wiflix")) { // allow to find the redirect url if it's changed
                mainUrl = newMainUrl
            } else {
                // i don't know why but clone feature seems not to work with wiflix then get the url from a file
                app.get("https://raw.githubusercontent.com/Eddy976/cloudstream-extensions-eddy/ressources/fetchwebsite.json")
                    .parsed<ArrayList<mediaData>>().forEach {
                        if (it.title.contains("wiflix", ignoreCase = true)) {
                            mainUrl = it.url
                        }
                    }
            }
        } catch (e: Exception) { // url changed
            app.get("https://raw.githubusercontent.com/Eddy976/cloudstream-extensions-eddy/ressources/fetchwebsite.json")
                .parsed<ArrayList<mediaData>>().forEach {
                    if (it.title.contains("wiflix", ignoreCase = true)) {
                        mainUrl = it.url
                    }
                }

        }
        if (mainUrl.endsWith("/")) mainUrl.dropLast(1)
        isNotInit = false
    }

    /**
    Cherche le site pour un titre spécifique

    La recherche retourne une SearchResponse, qui peut être des classes suivants: AnimeSearchResponse, MovieSearchResponse, TorrentSearchResponse, TvSeriesSearchResponse
    Chaque classes nécessite des données différentes, mais a en commun le nom, le poster et l'url
     **/
    override suspend fun search(query: String): List<SearchResponse> {
        val link =
            "$mainUrl/index.php?do=search&subaction=search&search_start=0&full_search=1&result_from=1&story=$query&titleonly=3&searchuser=&replyless=0&replylimit=0&searchdate=0&beforeafter=after&sortby=date&resorder=desc&showposts=0&catlist%5B%5D=0" // search'
        val document =
            app.post(link).document // app.get() permet de télécharger la page html avec une requete HTTP (get)
        val results = document.select("div#dle-content > div.clearfix")

        val allresultshome =
            results.mapNotNull { article ->  // avec mapnotnull si un élément est null, il sera automatiquement enlevé de la liste
                article.toSearchResponse()
            }
        return allresultshome
    }

    /**
     * charge la page d'informations, il ya toutes les donées, les épisodes, le résumé etc ...
     * Il faut retourner soit: AnimeLoadResponse, MovieLoadResponse, TorrentLoadResponse, TvSeriesLoadResponse.
     */
    data class EpisodeData(
        @JsonProperty("url") val url: String,
        @JsonProperty("episodeNumber") val episodeNumber: String,
    )

    private fun Elements.takeEpisode(
        url: String,
        posterUrl: String?,
        duborSub: String?
    ): ArrayList<Episode> {

        val episodes = ArrayList<Episode>()
        this.select("ul.eplist > li").forEach {

            val strEpisodeN =
                Regex("""pisode[\s]+(\d+)""").find(it.text())?.groupValues?.get(1).toString()
            val link =
                EpisodeData(
                    url,
                    strEpisodeN,
                ).toJson()


            episodes.add(
                Episode(
                    link + if (duborSub == "vostfr") {
                        "*$duborSub*"
                    } else {
                        ""
                    },
                    name = "Episode en " + duborSub,
                    episode = strEpisodeN.toIntOrNull(),
                    posterUrl = posterUrl
                )
            )
        }

        return episodes
    }

    override suspend fun load(url: String): LoadResponse {
        val document = avoidCloudflare(url).document //
        // url est le lien retourné par la fonction search (la variable href) ou la fonction getMainPage
        var subEpisodes = ArrayList<Episode>()
        var dubEpisodes = ArrayList<Episode>()
        val mediaType: TvType
        val episodeFrfound =
            document.select("div.blocfr")

        val episodeVostfrfound =
            document.select("div.blocvostfr")
        val title =
            document.select("h1[itemprop]").text()
        val posterUrl =
            document.select("img#posterimg").attr("src")
        val yearRegex = Regex("""ate de sortie\: (\d*)""")
        val year = yearRegex.find(document.text())?.groupValues?.get(1)


        val tags = document.select("[itemprop=genre] > a")
            .map { it.text() } // séléctione tous les tags et les ajoutes à une liste
        mediaType = TvType.TvSeries
        if (episodeFrfound.text().lowercase().contains("episode")) {
            val duborSub = "\uD83C\uDDE8\uD83C\uDDF5"
            dubEpisodes = episodeFrfound.takeEpisode(url, fixUrl(posterUrl), duborSub)
        }
        if (episodeVostfrfound.text().lowercase().contains("episode")) {
            val duborSub = "vostfr"
            subEpisodes = episodeVostfrfound.takeEpisode(url, fixUrl(posterUrl), duborSub)
        }
        ///////////////////////////////////////////
        ///////////////////////////////////////////
        var type_rec: TvType
        val recommendations =
            document.select("div.clearfixme > div > div").mapNotNull { element ->
                val recTitle =
                    element.select("a").text() ?: return@mapNotNull null
                val image = element.select("a >img").attr("src")
                val recUrl = element.select("a").attr("href")
                type_rec = TvType.TvSeries
                if (recUrl.contains("film")) type_rec = TvType.Movie

                if (type_rec == TvType.TvSeries) {
                    TvSeriesSearchResponse(
                        recTitle,
                        recUrl,
                        this.name,
                        TvType.TvSeries,
                        image?.let { fixUrl(it) },

                        )
                } else
                    MovieSearchResponse(
                        recTitle,
                        recUrl,
                        this.name,
                        TvType.Movie,
                        image?.let { fixUrl(it) },
                    )

            }

        val comingSoon = url.contains("films-prochainement")


        if (subEpisodes.isEmpty() && dubEpisodes.isEmpty()) {
            val description = document.selectFirst("div.screenshots-full")?.text()
                ?.replace("(.* .ynopsis)".toRegex(), "")
            return newMovieLoadResponse(
                name = title,
                url = url,
                type = TvType.Movie,
                dataUrl = url + if (document.select("span[itemprop*=\"inLanguage\"]").text()
                        .contains("vostfr", true)
                ) {
                    "*vostfr*"
                } else {
                    ""
                }

            ) {
                this.posterUrl = fixUrl(posterUrl)
                this.plot = description
                this.recommendations = recommendations
                this.year = year?.toIntOrNull()
                this.comingSoon = comingSoon
                this.tags = tags
            }
        } else {
            val description = document.selectFirst("span[itemprop=description]")?.text()
            return newAnimeLoadResponse(
                title,
                url,
                mediaType,
            ) {
                this.posterUrl = fixUrl(posterUrl)
                this.plot = description
                this.recommendations = recommendations
                this.year = year?.toIntOrNull()
                this.comingSoon = comingSoon
                this.tags = tags
                if (subEpisodes.isNotEmpty()) addEpisodes(DubStatus.Subbed, subEpisodes)
                if (dubEpisodes.isNotEmpty()) addEpisodes(DubStatus.Dubbed, dubEpisodes)

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
        var isvostfr = false
        val trueUrl: String
        val parsedInfo = if (data.takeLast(8) == "*vostfr*") {
            isvostfr = true
            trueUrl = data.dropLast(8)
            tryParseJson<EpisodeData>(data.dropLast(8))
        } else {
            trueUrl = data
            tryParseJson<EpisodeData>(data)
        }

        val url = parsedInfo?.url ?: trueUrl

        val numeroEpisode = parsedInfo?.episodeNumber

        val document = avoidCloudflare(url).document
        val episodeFrfound =
            document.select("div.blocfr")
        val episodeVostfrfound =
            document.select("div.blocvostfr")
        var flag = "\uD83C\uDDE8\uD83C\uDDF5"

        val cssCodeForPlayer = if (episodeFrfound.text().contains("Episode") && !isvostfr) {
            "div.ep${numeroEpisode}vf > a"

        } else if (episodeVostfrfound.text().contains("Episode")) {
            "div.ep${numeroEpisode}vs > a"

        } else {
            "div.linkstab > a"
        }

        if (cssCodeForPlayer.contains("vs") || isvostfr) {
            flag = " \uD83D\uDCDC \uD83C\uDDEC\uD83C\uDDE7"
        }


        document.select(cssCodeForPlayer).apmap { player -> // séléctione tous les players
            var playerUrl = "https" + player.attr("href").replace("(.*)https".toRegex(), "")
            if (!playerUrl.isBlank())
                if (playerUrl.contains("dood")) {
                    playerUrl = playerUrl.replace("doodstream.com", "dood.wf")
                }
            loadExtractor(
                httpsify(playerUrl),
                playerUrl,
                subtitleCallback
            ) { link ->
                callback.invoke(
                    ExtractorLink( // ici je modifie le callback pour ajouter des informations, normalement ce n'est pas nécessaire
                        link.source,
                        link.name + flag,
                        link.url,
                        link.referer,
                        getQualityFromName("HD"),
                        link.isM3u8,
                        link.headers,
                        link.extractorData
                    )
                )
            }
        }


        return true
    }

    private fun Element.toSearchResponse(): SearchResponse {

        val posterUrl = fixUrl(select("div.img-box > img").attr("src"))
        val qualityExtracted = select("div.nbloc1-2 >span").text()
        val type = select("div.nbloc3").text().lowercase()
        val title = select("a.nowrap").text()
        val link = select("a.nowrap").attr("href")
        val quality = getQualityFromString(
            when (!qualityExtracted.isNullOrBlank()) {
                qualityExtracted.contains("HDLight") -> "HD"
                qualityExtracted.contains("Bdrip") -> "BlueRay"
                qualityExtracted.contains("DVD") -> "DVD"
                qualityExtracted.contains("CAM") -> "Cam"

                else -> null
            }
        )
        if (type.contains("film")) {
            return newAnimeSearchResponse(
                name = title,
                url = link,
                type = TvType.Movie,

                ) {
                this.dubStatus = if (select("span.nbloc1").text().contains("vostfr", true)) {
                    EnumSet.of(DubStatus.Subbed)
                } else {
                    EnumSet.of(DubStatus.Dubbed)
                }
                this.posterUrl = posterUrl
                this.quality = quality

            }


        } else  // an Serie
        {

            return newAnimeSearchResponse(
                name = title,
                url = link,
                type = TvType.TvSeries,

                ) {
                this.posterUrl = posterUrl
                this.quality = quality
                addDubStatus(
                    isDub = !select("span.block-sai").text().uppercase().contains("VOSTFR"),
                    episodes = Regex("""pisode[\s]+(\d+)""").find(select("div.block-ep").text())?.groupValues?.get(
                        1
                    )?.toIntOrNull()
                )
            }

        }
    }


    suspend fun avoidCloudflare(url: String): NiceResponse {
        if (!app.get(url).isSuccessful) {
            return app.get(url, interceptor = interceptor)
        } else {
            return app.get(url)
        }
    }

    data class mediaData(
        @JsonProperty("title") var title: String,
        @JsonProperty("url") val url: String,
    )


    override val mainPage = mainPageOf(
        Pair("/films-prochainement/page/", "Film Prochainement en Streaming"),
        Pair("/film-en-streaming/page/", "Top Films cette année"),
        Pair("/serie-en-streaming/page/", "Top Séries cette année"),
        Pair("/saison-complete/page/", "Les saisons complètes"),
        Pair("/film-ancien/page/", "Film zahalé (ancien)")
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (isNotInit) initMainUrl()
        val url = mainUrl + request.data + page
        val document =
            avoidCloudflare(url).document

        //posterHeaders = interceptor.getCookieHeaders(url).toMap()

        val movies = document.select("div#dle-content > div.clearfix")

        val home =
            movies.mapNotNull { article ->  // avec mapnotnull si un élément est null, il sera automatiquement enlevé de la liste
                article.toSearchResponse()
            }
        return newHomePageResponse(request.name, home)
    }

}

