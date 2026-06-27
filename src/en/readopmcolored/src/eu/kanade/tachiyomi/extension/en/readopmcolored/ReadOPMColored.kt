package eu.kanade.tachiyomi.extension.en.readopmcolored

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.Calendar

class ReadOPMColored : ParsedHttpSource() {

    override val name = "ReadOPMColored"
    override val baseUrl = "https://www.readopmcolored.com"
    override val lang = "en"
    override val supportsLatest = false

    // This is a single-manga site, so we return the fixed main manga immediately
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create().apply {
            title = "One Punch Man (AI Colored)"
            url = "/"
            thumbnail_url = "$baseUrl/favicon.ico" // Or a fixed poster URL if available
            initialized = true
        }
        return Observable.just(MangasPage(listOf(manga), false))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        // Direct any search query or filtering back to the main series if it matches
        return if (query.isBlank() || "punch".contains(query, ignoreCase = true) || "opm".contains(query, ignoreCase = true)) {
            fetchPopularManga(page)
        } else {
            Observable.just(MangasPage(emptyList(), false))
        }
    }

    // --- Details Parsing ---
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = "One Punch Man (AI Colored)"
        author = "ONE"
        artist = "Yusuke Murata"
        description = document.select("p:contains(One Punch Man follows Saitama)").text().ifBlank {
            "One Punch Man follows Saitama, a hero who can defeat any opponent with a single punch but seeks to find a worthy opponent. Features AI-powered colorization."
        }
        status = SManga.ONGOING
        thumbnail_url = "$baseUrl/favicon.ico"
    }

    // --- Chapter List Parsing ---
    override fun chapterListSelector() = "a[href*=/chapter-], a[href*=/ch-]" 
    // Adjust selector based on actual link layout, target all anchor tags pointing to individual chapters

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val rawUrl = element.attr("href")
        url = if (rawUrl.startsWith("http")) rawUrl else baseUrl + rawUrl
        
        // Extract text like "Chapter 100"
        val text = element.text()
        name = text.ifBlank { "Chapter " + url.substringAfterLast("-") }
        
        // Quick fallback parsing for decimals/numbers to sort correctly
        chapter_number = name.substringAfter("Chapter ").substringBefore(" ").toFloatOrNull() ?: -1f
        date_upload = Calendar.getInstance().timeInMillis // Site doesn't explicitly list individual upload dates
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).distinctBy { it.url }.sortedByDescending { it.chapter_number }
    }

    // --- Page List Parsing (Reading Images) ---
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        
        // Select images inside the reader view container
        // Scrapes either standard <img> elements or dynamically injected data attributes 
        val images = document.select("div.reader-images img, div.chapter-container img, img[data-src], main img")
        
        images.forEachIndexed { index, element ->
            var imageUrl = element.attr("abs:data-src")
            if (imageUrl.isBlank()) {
                imageUrl = element.attr("abs:src")
            }
            if (imageUrl.isNotEmpty()) {
                pages.add(Page(index, "", imageUrl))
            }
        }
        return pages
    }

    // --- Boilerplate overrides required by ParsedHttpSource ---
    override fun popularMangaSelector() = throw UnsupportedOperationException("Not used")
    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException("Not used")
    override fun popularMangaNextPageSelector() = null

    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not used")
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("Not used")
    override fun latestUpdatesNextPageSelector() = null

    override fun searchMangaSelector() = throw UnsupportedOperationException("Not used")
    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException("Not used")
    override fun searchMangaNextPageSelector() = null
    
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")
}
