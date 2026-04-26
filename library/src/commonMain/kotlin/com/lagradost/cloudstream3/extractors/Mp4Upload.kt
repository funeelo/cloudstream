package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink

open class Mp4Upload : ExtractorApi() {
    override var name = "Mp4Upload"
    override var mainUrl = "https://www.mp4upload.com"
    override val requiresReferer = true

    private val idMatch = Regex("mp4upload\\.com/(embed-|)([A-Za-z0-9]+)")

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val realUrl = idMatch.find(url)?.groupValues?.get(2)?.let {
            "$mainUrl/embed-$it.html"
        } ?: url

        val response = app.get(
            realUrl,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0",
                "Referer" to mainUrl
            )
        )

        val text = getAndUnpack(response.text)

        val patterns = listOf(
            Regex("file\\s*:\\s*\"(.*?)\""),
            Regex("sources:\\s*\\[\\{file:\"(.*?)\""),
            Regex("player\\.src\\(\"(.*?)\"")
        )

        val links = mutableListOf<ExtractorLink>()

        for (regex in patterns) {
            val match = regex.find(text)?.groupValues?.getOrNull(1)
            if (match != null) {
                links.add(
                    newExtractorLink(name, name, match) {
                        this.referer = mainUrl
                    }
                )
            }
        }

        return links.ifEmpty { null }
    }
}