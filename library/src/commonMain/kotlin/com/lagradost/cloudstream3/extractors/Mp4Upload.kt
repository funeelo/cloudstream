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

    private val idMatch = Regex("""mp4upload\.com/(?:embed-)?([A-Za-z0-9]+)""")

    // beberapa varian yang pernah dipakai mp4upload
    private val srcRegexes = listOf(
        Regex("""player\.src\(\s*"(https?://[^"]+\.mp4[^"]*)"\s*\)"""),
        Regex("""player\.src\([\s\S]*?src\s*:\s*"(https?://[^"]+\.mp4[^"]*)""""),
        Regex("""file\s*:\s*"(https?://[^"]+\.mp4[^"]*)""""),
        Regex("""source\s+src\s*=\s*"(https?://[^"]+\.mp4[^"]*)"""")
    )

    private val qualityRegex = Regex("""\bheight\s*[:=]\s*"?(\d{3,4})""")

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val id = idMatch.find(url)?.groupValues?.get(1) ?: return null
        val embedUrl = "$mainUrl/embed-$id.html"

        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0 Safari/537.36",
            "Accept-Language" to "en-US,en;q=0.9"
        )

        val response = app.get(
            embedUrl,
            referer = referer ?: url,
            headers = headers
        ).text

        val unpacked = runCatching { getAndUnpack(response) }.getOrNull().orEmpty()
        val haystack = if (unpacked.isNotBlank()) unpacked else response

        val videoLink = srcRegexes.firstNotNullOfOrNull {
            it.find(haystack)?.groupValues?.getOrNull(1)
        } ?: return null

        val quality = qualityRegex.find(haystack)
            ?.groupValues?.get(1)?.toIntOrNull()
            ?: Qualities.Unknown.value

        return listOf(
            newExtractorLink(
                source = name,
                name = name,
                url = videoLink,
            ) {
                this.referer = embedUrl
                this.quality = quality
            }
        )
    }
}