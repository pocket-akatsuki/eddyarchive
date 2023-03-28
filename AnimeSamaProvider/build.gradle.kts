// use an integer for version numbers
version = 1


cloudstream {
    language = "fr"
    // All of these properties are optional, you can safely remove them

    description = "Découvrez ce site qui regroupe les derniers animes et les scans. C'est un site pour les fans de manga. Tout y est"
    authors = listOf("Eddy")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Anime",
		"AnimeMovie",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=anime-sama.fr&sz=%size%"
}