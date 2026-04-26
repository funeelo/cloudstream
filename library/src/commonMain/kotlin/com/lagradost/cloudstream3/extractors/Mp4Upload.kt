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

    override suspend fun getUrl(
        url: String,
        referer: String?
    ): List<ExtractorLink>? {

        val realUrl = idMatch.find(url)?.groupValues?.get(2)?.let { id ->
            "$mainUrl/embed-$id.html"
        } ?: url

        val response = app.get(
            realUrl,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                "Referer" to mainUrl,
                "Accept" to "*/*",
                "Origin" to mainUrl
            )
        )

        val text = getAndUnpack(response.text)

        // 🔍 DEBUG (penting kalau masih gagal)
        println("MP4UPLOAD HTML:")
        println(text)

        val links = mutableListOf<ExtractorLink>()

        val patterns = listOf(
            Regex("file\\s*:\\s*\"(https?://[^\"']+)\""),
            Regex("sources:\\s*\\[\\{file:\"(https?://[^\"']+)\""),
            Regex("player\\.src\\(\"(https?://[^\"']+)\""),
            Regex("src\\s*:\\s*\"(https?://[^\"']+)\"")
        )

        for (regex in patterns) {
            val match = regex.find(text)?.groupValues?.getOrNull(1)
            if (match != null) {
                links.add(
                    newExtractorLink(
                        name,
                        name,
                        match
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }

        return links.ifEmpty { null }
    }
}