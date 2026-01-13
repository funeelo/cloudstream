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
    
    // Multiple regex patterns to catch different player formats
    private val srcRegex = Regex("""player\.src\s*\(\s*["']([^"']+)["']""")
    private val srcRegex2 = Regex("""player\.src\s*\(\s*\{[^}]*src\s*:\s*["']([^"']+)["']""")
    private val srcRegex3 = Regex("""["']?file["']?\s*:\s*["']([^"']+\.mp4[^"']*)["']""")
    private val srcRegex4 = Regex("""src["']?\s*:\s*["']([^"']+\.mp4[^"']*)["']""")

    override val requiresReferer = true
    private val idMatch = Regex("""mp4upload\.com/(?:embed-)?([A-Za-z0-9]+)""")
    
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val id = idMatch.find(url)?.groupValues?.get(1) ?: return null
        val embedUrl = "$mainUrl/embed-$id.html"
        
        val response = app.get(
            embedUrl,
            headers = mapOf(
                "Referer" to (referer ?: mainUrl),
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                "Accept-Language" to "en-US,en;q=0.5",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
        )
        
        val text = response.text
        val unpackedText = try {
            getAndUnpack(text)
        } catch (e: Exception) {
            text // Fallback to original text if unpacking fails
        }
        
        // Extract quality from multiple possible locations
        val quality = Regex("""height[=:\s]+(\d+)""")
            .find(unpackedText.lowercase())
            ?.groupValues?.get(1)?.toIntOrNull()
        
        // Try all regex patterns
        val regexList = listOf(srcRegex, srcRegex2, srcRegex3, srcRegex4)
        
        for (regex in regexList) {
            regex.find(unpackedText)?.groupValues?.get(1)?.let { link ->
                if (link.isNotBlank() && link.startsWith("http")) {
                    return listOf(
                        newExtractorLink(
                            name,
                            name,
                            link,
                        ) {
                            this.referer = embedUrl
                            this.quality = quality ?: Qualities.Unknown.value
                        }
                    )
                }
            }
        }
        
        return null
    }
}