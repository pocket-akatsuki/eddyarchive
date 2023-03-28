// use an integer for version numbers
version = 2


cloudstream {
    language = "fr"
    // All of these properties are optional, you can safely remove them

    description = "Empire Streaming regroupe Netflix, Amazon Prime video, Apple Tv, Disney plus ..."
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
        "TvSeries",
		"Movie",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=empire-streaming.co&sz=%size%"
}