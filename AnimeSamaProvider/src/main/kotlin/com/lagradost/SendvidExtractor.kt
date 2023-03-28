package com.lagradost
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app


open class SendvidExtractor : ExtractorApi() {
    override val name: String = "Sendvid"
    override val mainUrl: String = "https://sendvid.com"
    override val requiresReferer = false


    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val document = app.get(url).document
        val link = document.select("[property= og:video]").attr("content")
        return listOf(
            ExtractorLink(
                name,
                name,
                link,
                url, // voir si site demande le referer à mettre ici
                Qualities.Unknown.value,
                isM3u8 = true,
            )
        )

    }


}



