package com.lagradost


import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.nicehttp.NiceResponse
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.util.*


class AnimeSamaProvider : MainAPI() {
    override var mainUrl = "https://anime-sama.fr"
    override var name = "Anime-sama"
    override val hasQuickSearch = false // recherche rapide (optionel, pas vraimet utile)
    override val hasMainPage = true // page d'accueil (optionel mais encoragé)
    override var lang = "fr" // fournisseur est en francais
    override val supportedTypes =
        setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA) // animes, animesfilms

    /**
    Cherche le site pour un titre spécifique

    La recherche retourne une SearchResponse, qui peut être des classes suivants: AnimeSearchResponse, MovieSearchResponse, TorrentSearchResponse, TvSeriesSearchResponse
    Chaque classes nécessite des données différentes, mais a en commun le nom, le poster et l'url
     **/
    override suspend fun search(query: String): List<SearchResponse> {
        val allresultshome: MutableList<SearchResponse> = mutableListOf()
        val link = "$mainUrl/search/search.php?terme=$query&s=Search" // search'
        val document =
            app.get(link).document // app.get() permet de télécharger la page html avec une requete HTTP (get)
        val results = document.select("div.search_text > div.bd-highlight")
        results.apmap { article ->  // avec mapnotnull si un élément est null, il sera automatiquement enlevé de la liste
            allresultshome.toSearchResponse1(article)
        }
        return allresultshome

    }

    val regexGetlink = Regex("""(http.*)\'\,""")
    private fun Element.toSearchResponseAll(posterUrl: String?): SearchResponse {

        val text = this.text()
        val title = text
        val link_on_click = this.attr("onclick")
        val link =
            regexGetlink.find(link_on_click)?.groupValues?.get(1) ?: throw ErrorLoadingException()
        val dubstatus = if (title.lowercase().contains("vostfr")) {
            EnumSet.of(DubStatus.Subbed)
        } else {
            EnumSet.of(DubStatus.Dubbed)
        }
        var tvtype = TvType.Anime
        if (text.lowercase().contains("film")) {
            tvtype = TvType.AnimeMovie
        }

        return newAnimeSearchResponse(
            title,
            link,
            tvtype,
            false,
        ) {
            this.posterUrl = posterUrl
            this.dubStatus = dubstatus
        }
        //}
    }

    private fun Element.toSearchResponse_all_rec(posterUrl: String?, link: String): SearchResponse {

        val title = this.text()
        val dubstatus = if (title.lowercase().contains("vostfr")) {
            EnumSet.of(DubStatus.Subbed)
        } else {
            EnumSet.of(DubStatus.Dubbed)
        }
        var tvtype = TvType.Anime
        if (title.lowercase().contains("film")) {
            tvtype = TvType.AnimeMovie
        }

        return newAnimeSearchResponse(
            title,
            link,
            tvtype,
            false,
        ) {
            this.posterUrl = posterUrl
            this.dubStatus = dubstatus
        }
        //}
    }

    private suspend fun MutableList<SearchResponse>.toSearchResponse1(element: Element) {
        val figcaption = element.select(" div.media-body > div >a > h5").text()
        if (!figcaption.lowercase().trim().contains("scan")) {
            val posterUrl = element.select("div.bd-highlight >div >a>img ").attr("src")
            val link_to_anime = element.select("div >a").attr("href")
            val document = app.get(link_to_anime).document
            val all_anime = document.select("div.synsaisons > li")
            all_anime.forEach { saga -> this.add(saga.toSearchResponseAll(posterUrl)) }
        }

    }

    /**
     * charge la page d'informations, il ya toutes les donées, les épisodes, le résumé etc ...
     * Il faut retourner soit: AnimeLoadResponse, MovieLoadResponse, TorrentLoadResponse, TvSeriesLoadResponse.
     */
    private data class ResultsForLoop(
        var episode_tite: String, // increment for selecting the next idxEndForLoop
        var epNo: Int?
    )

    private data class DataForLoop(

        var idx_EpSpec: Int, // increment special episode number
        var idx_Ep: Int, // Start loop at episode idx_Ep (nextIdxBeginLoop)
        var idBeginLoop: Int, // Increment for selecting the next idx_Ep
        var idxEndForLoop: Int, // End loop at episode idxEndForLoop
        var idEndLoop: Int, // increment for selecting the next idxEndForLoop
        var nbrEpSpec: Int,
        var results: ResultsForLoop,

        )

    private data class DataSet(
        val isTitleEp: Boolean,
        val all_title: Elements,
        val nbrBeginloop: Int, // number of (begin for loop i = N) found by regex
        val nbrEndloop: Int, // number of (end for loop i <= N) found by regex
        val allstartForLoop: Sequence<MatchResult>,
        val allEndForLoop: Sequence<MatchResult>,
    )


    private suspend fun String.findPosterfromEmbedUrl(): String {
        val link_video = this
        var openlink: String
        var link_poster = ""
        when (!link_video.isBlank()) {
            link_video.contains("video.sibnet") -> {
                openlink = Regex("""[^']*video\.sibnet[^']*""").find(
                    link_video
                )?.groupValues?.get(0).toString()


                link_poster = app.get(
                    openlink
                ).document.select("[property=og:image]").attr("content")


            }
            link_video.contains("sendvid") -> {
                openlink = Regex("""[^']*sendvid[^']*""").find(
                    link_video
                )?.groupValues?.get(0).toString()

                link_poster = app.get(
                    openlink
                ).document.select("[property=og:image]").attr("content")

            }
            link_video.contains("myvi.top") -> {
                openlink = Regex("""[^']*myvi\.top[^']*""").find(link_video)?.groupValues?.get(
                    0
                ).toString()


                link_poster = Regex("""([^=]*myvi[^\\]*\.[j]pg[n]*[^\\]*)""").find(
                    app.get(openlink).text
                )?.groupValues?.get(1).toString().replace("%2f", "/").replace("%3a", ":")
                    .replace("%3f", "?").replace("%3d", "=").replace("%26", "&")

            }
            link_video.contains("myvi.tv") -> {
                openlink = Regex("""[^']*myvi\.tv[^']*""").find(link_video)?.groupValues?.get(
                    0
                ).toString()


                link_poster = Regex("""([^=]*myvi[^\\]*\.[j]pg[n]*[^\\]*)""").find(
                    app.get(openlink).text
                )?.groupValues?.get(1).toString().replace("%2f", "/").replace("%3a", ":")
                    .replace("%3f", "?").replace("%3d", "=").replace("%26", "&")

            }

            link_video.contains("myvi.ru") -> {
                openlink = Regex("""[^']*myvi\.ru[^']*""").find(link_video)?.groupValues?.get(
                    0
                ).toString()
                if (openlink.contains("http")) {
                    openlink = "http:$openlink"
                }
                link_poster = Regex("""([^=]*myvi[^\\]*\.[j]pg[n]*[^\\]*)""").find(
                    app.get(openlink).text
                )?.groupValues?.get(1).toString().replace("%2f", "/").replace("%3a", ":")
                    .replace("%3f", "?").replace("%3d", "=").replace("%26", "&")

            }

            else -> return link_poster
        }
        return link_poster
    }

    private fun loopLookingforEpisodeTitle(dataLoop: DataForLoop, dataset: DataSet): DataForLoop {
        val episode_tite: String
        val epNo: Int?
        val results = ResultsForLoop("", null)
        var idx_Ep = dataLoop.idx_Ep
        var idxEndForLoop = dataLoop.idxEndForLoop
        var idBeginLoop = dataLoop.idBeginLoop
        var nbrEpSpec = dataLoop.nbrEpSpec
        val nbrBeginloop = dataset.nbrBeginloop
        var idEndLoop = dataLoop.idEndLoop
        var idx_EpSpec = dataLoop.idx_EpSpec
        val nextidxEndForLoop: Int
        val nextIdxBeginLoop: Int
        if (dataset.isTitleEp) {
            episode_tite = dataset.all_title[idx_Ep - 1].text()//
            idx_Ep++
            epNo = null
        } else {
            if ((idx_Ep > idxEndForLoop || nbrEpSpec > 1) && (idBeginLoop + 1) < nbrBeginloop) {
                if (dataLoop.idx_Ep > dataLoop.idxEndForLoop) {
                    idBeginLoop++


                    nextIdxBeginLoop =
                        dataset.allstartForLoop.elementAt(idBeginLoop).groupValues.get(1).toInt()
                    idx_Ep = nextIdxBeginLoop

                    nbrEpSpec = nextIdxBeginLoop - idxEndForLoop
                    if ((idEndLoop + 1) < dataset.nbrEndloop) {
                        idEndLoop++
                        nextidxEndForLoop =
                            dataset.allEndForLoop.elementAt(idEndLoop).groupValues.get(1).toInt()
                    } else {
                        nextidxEndForLoop = 150000 // end
                    }

                    idxEndForLoop = nextidxEndForLoop
                }

                episode_tite = "Episode Special $idx_EpSpec"
                epNo = null
                idx_EpSpec++
                nbrEpSpec--
            } else {
                episode_tite = "Episode $idx_Ep"
                epNo = idx_Ep
                idx_Ep++
            }
        }
        results.episode_tite = episode_tite
        results.epNo = epNo
        return DataForLoop(
            idx_EpSpec, // increment special episode number
            idx_Ep, // Start loop at episode idx_Ep (nextIdxBeginLoop)
            idBeginLoop, // Increment for selecting the next idx_Ep
            idxEndForLoop, // End loop at episode idxEndForLoop
            idEndLoop, // increment for selecting the next idxEndForLoop
            nbrEpSpec, results
        )
        //}
    }

    fun getFlag(sequence: String): String {

        val flag: String
        flag = when (true) {
            sequence.uppercase().contains("VF") -> "\uD83C\uDDE8\uD83C\uDDF5"
            sequence.uppercase().contains("VOSTFR") -> "\uD83C\uDDEF\uD83C\uDDF5"
            else -> ""

        }
        return flag
    }

    suspend fun ArrayList<Episode>.getEpisodes(
        html: NiceResponse,
        url: String,
    ) {
        //val flag = getFlag(dubStatus)
        val document = html.document
        val scpritAllEpisode =
            document.select("script[src*=\"filever\"]").attr("src") ?: "episodes.js"
        val url_scriptEp = if (url.takeLast(1) != "/") {
            "$url/$scpritAllEpisode"
        } else {
            "$url$scpritAllEpisode"
        }
        val getScript = app.get(url_scriptEp)
        val text_script = getScript.text
        val resultsAllContent = regexAllcontentEpisode.findAll(text_script)
        //////////////////////////////////////
        /////////////////////////////////////
        var idx_EpStart: Int
        ///////////////////////////////////
        /////////////////////////////////

        val all_title = document.select("select#selectEps.episodes > option")
        val isTitleEp = !all_title.isNullOrEmpty()

        val idBeginLoop = 0
        val idEndLoop = 0
        val allstartForLoop = regexCreateEp.findAll(html.text)
        val allEndForLoop = regexgetLoopEnd.findAll(html.text)

        var idxEndForLoop: Int
        var nbrEndloop: Int // number of end for loop found
        var nbrBeginloop: Int // number of begin for loop found
        try {
            idx_EpStart = allstartForLoop.elementAt(idBeginLoop).groupValues.get(1).toInt()
            nbrBeginloop = allstartForLoop.count()
            if (idx_EpStart >= 0) {
                try {
                    idxEndForLoop = allEndForLoop.elementAt(idEndLoop).groupValues.get(1).toInt()
                    nbrEndloop = allEndForLoop.count()

                } catch (e: Exception) {
                    idxEndForLoop = 150000 // one for loop
                    nbrEndloop = 1

                }
            } else {
                idxEndForLoop = 1
                nbrEndloop = 0
            }

        } catch (e: Exception) {
            idx_EpStart = 1
            idxEndForLoop = 1
            nbrEndloop = 0
            nbrBeginloop = 0
        }
        val idx_EpSpec = 1
        val nbrEpSpec = 0
        val results = ResultsForLoop("", null)
        // the site use a for loop and add by hand special episode ! so we have to detect when an episode is added by hand
        var dataLoop = DataForLoop(
            idx_EpSpec, // increment special episode number
            idx_EpStart, // Start loop at episode idx_Ep (nextIdxBeginLoop)
            idBeginLoop, // Increment for selecting the next idx_Ep
            idxEndForLoop, // End loop at episode idxEndForLoop
            idEndLoop, // increment for selecting the next idxEndForLoop
            nbrEpSpec, results
        )
        val dataset = DataSet(
            // the site use a for loop and add by hand episode ! so we have to detect when an episode is added by hand
            isTitleEp,
            all_title,
            nbrBeginloop, // number of (begin for loop i = N) found by regex
            nbrEndloop, // number of (end for loop i <= N) found by regex
            allstartForLoop,
            allEndForLoop,
        )
        var concatAll = ""
        resultsAllContent.forEach {
            concatAll += it.groupValues[0].replace(
                """[\s]*\/\/[\s]*[^\,]+[\s]*\n""".toRegex(),
                ""
            )
                .replace("\n", "").replace("\t", "").replace("""[\s]*""".toRegex(), "")
        }
        concatAll = concatAll.replace("][", "*").replace("[", "*")

        var sumlink = ""
        while (concatAll.contains("*'")) {
            Regex("""\*'[^']*'""").findAll(concatAll).forEach {
                concatAll =
                    concatAll.replace("${it.groupValues[0]},", "*").replace(it.groupValues[0], "*")
                sumlink += it.groupValues[0] + ","
            }
            dataLoop = loopLookingforEpisodeTitle(dataLoop, dataset)

            this.add(
                Episode(
                    data = sumlink,
                    episode = dataLoop.results.epNo,
                    name = dataLoop.results.episode_tite,
                    //posterUrl = link_poster
                )
            )
            sumlink = ""
        }
    }

    private val regexAllcontentEpisode = Regex("""\[[^\]]*]""")
    private val regexCreateEp = Regex("""for[\s]+\(var[\s]+i[\s]+=[\s]+([0-9]+)[\s]*;""")
    private val regexgetLoopEnd = Regex("""i[\s]*<=[\s]*([0-9]+)""")

    fun dropSlachChar(url: String): String {
        return if (url.takeLast(1) == "/") {
            url.dropLast(1)
        } else {
            url
        }
    }

    fun findOrigintitle(html: NiceResponse, url: String): String {
        html.document.select("div.synsaisons > li").forEach { saga ->
            val link_on_click = saga.attr("onclick")
            val link = regexGetlink.find(link_on_click)?.groupValues?.get(1)
                ?: throw ErrorLoadingException()
            if (dropSlachChar(url) == dropSlachChar(link)) {
                return saga.text()
            }
        }
        return ""
    }

    fun findlinkforSuborDub(html: NiceResponse, url: String): String {
        val recommendations = html.document.select("div.synsaisons > li")
        val titleInit =
            findOrigintitle(html, url).uppercase().replace("VOSTFR", "").replace("VF", "")
                .replace("FILMS", "").replace("FILM", "").replace("""\s*""".toRegex(), "").trim()
        recommendations.forEach { saga ->
            val link_on_click = saga.attr("onclick")
            val link = regexGetlink.find(link_on_click)?.groupValues?.get(1)
                ?: throw ErrorLoadingException()
            val titleL =
                saga.text().uppercase().replace("VOSTFR", "").replace("VF", "").replace("FILMS", "")
                    .replace("FILM", "").replace("""\s*""".toRegex(), "").trim()
            if (titleL == titleInit && dropSlachChar(link) != dropSlachChar(url)) {
                return link
            }
        }
        return url
    }

    override suspend fun load(url: String): LoadResponse {
        var targetUrl = url
        if (url.contains("*")) {
            val (link, _) = app.get(url.replace("*", "")).document.select("div.synsaisons > li")
                .tryTofindLatestSeason()
            targetUrl = link.toString()

        }
        val subEpisodes = ArrayList<Episode>()
        val dubEpisodes = ArrayList<Episode>()

        val html = app.get(targetUrl)
        val document = html.document
        val textLinkBack = document.select("p.soustitreaccueil.syntitreanime").attr("onclick")
            ?: throw ErrorLoadingException()
        val linkBack =
            rgxGetLink.find(textLinkBack)!!.groupValues.get(0).replace("'", "").replace(",", "")
        val htmlBack = app.get(linkBack)
        val documentBack = htmlBack.document
        val description = documentBack.select("div.carousel-caption > p")[0].text()
        val poster = documentBack.select("img.d-block.w-100")[0].attr("src")
        var title = document.select("p.soustitreaccueil.syntitreanime").text()
        var status = false
        val urlSubDub = findlinkforSuborDub(htmlBack, targetUrl)
        var htmlSubDub: NiceResponse? = null
        if (urlSubDub != targetUrl) {
            htmlSubDub = app.get(urlSubDub)
        }
        if (title.lowercase().contains("vostfr")) {

            listOf("SUB", "DUB").apmap {
                if (it == "SUB") {
                    subEpisodes.getEpisodes(html, targetUrl)
                    if (subEpisodes.isEmpty()) status = true
                }
                if (it == "DUB" && htmlSubDub != null) {
                    dubEpisodes.getEpisodes(htmlSubDub, urlSubDub)
                    if (dubEpisodes.isNotEmpty()) {
                        title = title.replace("VOSTFR", "").replace("VF", "")
                    }
                }
            }
        } else {
            listOf("SUB", "DUB").apmap {
                if (it == "SUB" && htmlSubDub != null) {
                    subEpisodes.getEpisodes(htmlSubDub, urlSubDub)
                    if (subEpisodes.isNotEmpty()) {
                        title = title.replace("VOSTFR", "").replace("VF", "")
                    }

                }
                if (it == "DUB") {
                    dubEpisodes.getEpisodes(html, targetUrl)
                    if (dubEpisodes.isEmpty()) status = true
                }
            }

        }

        listOf(dubEpisodes, subEpisodes).apmap {
            it.apmap { episode ->
                episode.posterUrl = poster//episode.data.findPosterfromEmbedUrl()
            }
        }

        val recommendations = documentBack.select("div.synsaisons > li")
        val allresultshome: MutableList<SearchResponse> = mutableListOf()
        recommendations.forEach { saga ->
            val link_on_click = saga.attr("onclick")
            val link = regexGetlink.find(link_on_click)?.groupValues?.get(1)
                ?: throw ErrorLoadingException()
            if (!(dropSlachChar(link) == dropSlachChar(targetUrl) || dropSlachChar(link) == dropSlachChar(
                    urlSubDub
                ))
            ) {
                allresultshome.add(saga.toSearchResponse_all_rec(poster, link))
            }
        }

        return newAnimeLoadResponse(
            title,
            targetUrl,
            TvType.Anime,
        ) {
            posterUrl = poster
            this.plot = description
            this.recommendations = allresultshome
            if (subEpisodes.isNotEmpty()) addEpisodes(DubStatus.Subbed, subEpisodes)
            if (dubEpisodes.isNotEmpty()) addEpisodes(DubStatus.Dubbed, dubEpisodes)
            this.comingSoon = status
        }

    }


    /** récupere les liens .mp4 ou m3u8 directement à partir du paramètre data généré avec la fonction load()**/
    val rgxGetLink = Regex("""'[^']*',""")
    override suspend fun loadLinks(
        data: String, // fournit par load()
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {

        val results = rgxGetLink.findAll(data)

        results.forEach { link ->

            val playerUrl = link.groupValues[0].replace("'", "").replace(",", "")

            if (!playerUrl.isBlank()) loadExtractor(
                httpsify(playerUrl), playerUrl, subtitleCallback
            ) { link ->
                callback.invoke(
                    ExtractorLink(
                        link.source,
                        link.name + "",
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

    private fun List<Element>.tryTofindLatestSeason(): Pair<String?, String?> {
        var link: String? = ""
        var i = 0
        var sum = 0
        var sumVost = 0
        var newSum = 0
        var newSumVost = 0
        var newSumMovie = 0
        var sumMovie = 0
        var text: String
        var detect_anime_Vostfr: Boolean
        var detect_anime_fr: Boolean
        var isVostfr = false
        var isFR = false
        var dubStatus: String? = ""
        while (i < this.size) {

            text = this[i].text()
            val a = text.lowercase().contains("vostfr")
            val b = text.lowercase().contains("film")
            val c = text.lowercase().contains("oav")
            detect_anime_Vostfr = a && !b && !c
            detect_anime_fr = !a && !b && !c
            if (detect_anime_Vostfr) {
                isVostfr = true

                findAllNumber.findAll(text).toList().apmap { number ->
                    newSumVost += number.groupValues[1].toInt()

                }
                if (newSumVost >= sumVost) {
                    sumVost = newSumVost
                    val link_on_click = this[i].attr("onclick") ?: throw ErrorLoadingException()
                    link = regexGetlink.find(link_on_click)?.groupValues?.get(1)
                    dubStatus = "vostfr"
                }
            } else if (!isVostfr && detect_anime_fr) {
                isFR = true
                findAllNumber.findAll(text).toList().apmap { number ->
                    newSum += number.groupValues[1].toInt()
                }
                if (newSum >= sum) {
                    sum = newSum
                    val link_on_click = this[i].attr("onclick") ?: throw ErrorLoadingException()
                    link = regexGetlink.find(link_on_click)?.groupValues?.get(1)
                    dubStatus = "fr"
                }
            } else if (!isVostfr && !isFR) {
                findAllNumber.findAll(text).toList().apmap { number ->
                    newSumMovie += number.groupValues[1].toInt()
                }
                if (newSumMovie >= sumMovie) {
                    sumMovie = newSumMovie
                    val link_on_click = this[i].attr("onclick") ?: throw ErrorLoadingException()
                    link = regexGetlink.find(link_on_click)?.groupValues?.get(1)
                    dubStatus = "film"
                }
            }
            newSumMovie = 0
            newSumVost = 0
            newSum = 0
            i++
        }

        return (link to dubStatus)
    }

    val findAllNumber = Regex("""([0-9]+)""")
    private fun Element.toSearchResponse(): SearchResponse? {
        val figcaption = select("a >figcaption > span").text()
        if (figcaption.lowercase().trim() != "scan") {
            val posterUrl = select("a > img").attr("src")
            val title = select("a >figcaption").text().replace(figcaption, "")
            val global_link = select("a").attr("href")
            if (global_link.contains("search.php")) {
                return null
            }

            val tv_type = TvType.TvSeries

            return newAnimeSearchResponse(
                title,
                "$global_link*",
                tv_type,
                false,
            ) {
                this.posterUrl = posterUrl
            }
        } else {
            return null
        }
    }

    private fun Element.toSearchResponseNewEp(): SearchResponse? {
        val figcaption = select("a >figcaption > span").text()
        val link = select("a").attr("href")
        if (figcaption.lowercase().trim() != "scan" && !link.lowercase().contains("/scan/")) {
            val posterUrl = select("a > img").attr("src")
            //val type = figcaption.lowercase()
            val scheduleTime = select("a >span.badgeHautDroite").text()
            val title = select("a >figcaption").text().replace(figcaption, "")

            if (link.contains("search.php")) {
                return null
            }

            val dubstatus = if (figcaption.lowercase().contains("vf")) {
                EnumSet.of(DubStatus.Dubbed)
            } else {
                EnumSet.of(DubStatus.Subbed)
            }
            val tv_type = TvType.Anime

            return newAnimeSearchResponse(
                "$scheduleTime \n $title",
                link,
                tv_type,
                false,
            ) {
                this.posterUrl = posterUrl
                this.dubStatus = dubstatus
            }

        } else {
            return null
        }

    }

    override val mainPage = mainPageOf(
        Pair(mainUrl, "NOUVEAUX"),
        Pair(mainUrl, "A ne pas rater"),
        Pair(mainUrl, "Les classiques"),
        Pair(mainUrl, "Derniers animes ajoutés"),
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        var categoryName = request.name

        val cssSelector: String
        val cssSelectorN: String

        val url = request.data
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_WEEK)
        val idDay: String

        idDay = when (today) {

            Calendar.MONDAY -> {
                "1"
            }
            Calendar.TUESDAY -> {
                "2"
            }
            Calendar.WEDNESDAY -> {
                "3"
            }
            Calendar.THURSDAY -> {
                "4"
            }
            Calendar.FRIDAY -> {
                "5"
            }
            Calendar.SATURDAY -> {
                "6"
            }
            else -> "0"
        }
        var home: List<SearchResponse> = mutableListOf()

        if (page <= 1) {
            val document = app.get(url).document
            cssSelector = "div.container-fluid>div#sectionsAccueil"
            cssSelectorN = "div#$idDay>div#sectionsAccueil > figure"
            home = when (!categoryName.isBlank()) {
                categoryName.contains("NOUVEAUX") -> {
                    categoryName =
                        document.select("div#$idDay.fadeJours > div.col-12>p.titreJours").text()
                    document.select(cssSelectorN)
                        .mapNotNull { article -> article.toSearchResponseNewEp() }
                }
                categoryName.contains("ajoutés") -> {
                    document.select(cssSelector)[2].select("figure")
                        .mapNotNull { article -> article.toSearchResponse() }
                }
                categoryName.contains("rater") -> {
                    document.select(cssSelector)[1].select("figure")
                        .mapNotNull { article -> article.toSearchResponse() }
                }
                else -> {
                    document.select(cssSelector)[0].select("figure")
                        .mapNotNull { article -> article.toSearchResponse() }
                }
            }
        }
        return newHomePageResponse(categoryName, home)
    }
}