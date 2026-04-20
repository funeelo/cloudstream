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

    private val srcRegexes = listOf(
        Regex("""player\.src\(\s*"(https?://[^"]+)"\s*\)"""),
        Regex("""player\.src\([\s\S]*?src\s*:\s*"(https?://[^"]+)""""),
        Regex("""\bfile\s*:\s*"(https?://[^"]+)""""),
        Regex("""<source[^>]+src\s*=\s*"(https?://[^"]+)"""")
    )

    private val qualityRegex = Regex("""\bheight\s*[:=]\s*"?(\d{3,4})""")

    private val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val id = idMatch.find(url)?.groupValues?.get(1) ?: return null
        val embedUrl = "$mainUrl/embed-$id.html"
        val watchUrl = "$mainUrl/$id"

        val baseHeaders = mapOf(
            "User-Agent" to ua,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
            "Sec-Fetch-Dest" to "iframe",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "cross-site"
        )

        val cookieJar = mutableMapOf<String, String>()
        runCatching {
            val primer = app.get(watchUrl, headers = baseHeaders, referer = referer)
            cookieJar.putAll(primer.cookies)
        }

        val embedResp = app.get(
            embedUrl,
            headers = baseHeaders,
            referer = referer ?: watchUrl,
            cookies = cookieJar
        )
        cookieJar.putAll(embedResp.cookies)

        val raw = embedResp.text
        val unpacked = runCatching { getAndUnpack(raw) }.getOrNull().orEmpty()
        val haystack = (unpacked + "\n" + raw)

        val videoLink = srcRegexes.firstNotNullOfOrNull {
            it.find(haystack)?.groupValues?.getOrNull(1)
        }?.trim() ?: return null

        val headResp = runCatching {
            app.head(
                videoLink,
                headers = mapOf(
                    "User-Agent" to ua,
                    "Referer" to embedUrl,
                    "Origin" to mainUrl,
                    "Range" to "bytes=0-"
                ),
                cookies = cookieJar,
                allowRedirects = true
            )
        }.getOrNull()

        if (headResp != null && headResp.code !in 200..299 && headResp.code != 206) {
            return null
        }

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
                this.headers = mapOf(
                    "User-Agent" to ua,
                    "Origin" to mainUrl,
                    "Referer" to embedUrl
                )
            }
        )
    }
}