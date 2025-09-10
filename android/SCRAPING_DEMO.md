# PDF URL Scraping Demo

This demonstrates the lightweight PDF URL scraping functionality using OkHttp + regex instead of Jsoup.

## What Changed

### Before (Heavy)
- **Jsoup dependency** - Full HTML DOM parser
- **Complex DOM traversal** - Heavy memory usage
- **Additional library** - Extra APK size

### After (Lightweight)
- **OkHttp only** - Already in project
- **Regex patterns** - Built-in Kotlin regex
- **No additional dependencies** - Zero extra APK size

## How It Works

1. **HTTP Request**: Uses OkHttp to fetch the HTML page
2. **Regex Parsing**: Uses simple regex patterns to find PDF links
3. **Context Analysis**: Looks for region names in link text and surrounding context
4. **URL Resolution**: Converts relative URLs to absolute URLs

## Key Benefits

- ✅ **Zero additional dependencies** - Uses existing OkHttp
- ✅ **Lightweight** - No DOM parsing overhead
- ✅ **Fast** - Simple regex is much faster than DOM traversal
- ✅ **Reliable** - Less prone to HTML structure changes
- ✅ **Maintainable** - Easy to understand and modify

## Regex Patterns Used

```kotlin
// Find PDF links
val pdfLinkPattern = Regex("""<a[^>]*href="([^"]*\.pdf)"[^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)

// Find headings for region context
val headingPattern = Regex("""<h[1-6][^>]*>(.*?)</h[1-6]>""", RegexOption.IGNORE_CASE)

// Find update dates
val datePattern = Regex("actualización[^\\d]*(\\d{1,2}[^\\d]*\\d{4})", RegexOption.IGNORE_CASE)
```

## Testing

### Manual Test
```bash
# Run the demo
cd android
./gradlew test --tests PDFURLScrapingServiceTest
```

### In App
The scraping runs automatically at app startup. Check logcat for output:
```
D/PDFURLScrapingService: Starting PDF URL scraping from https://cofsegovia.com/farmacias-de-guardia/
D/PDFURLScrapingService: Successfully fetched HTML content (12345 chars)
D/PDFURLScrapingService: Found 4 PDF links in HTML
D/PDFURLScrapingService: ===== SCRAPED PDF URLS =====
D/PDFURLScrapingService: 1. Segovia Capital
D/PDFURLScrapingService:    URL: https://cofsegovia.com/wp-content/uploads/2025/05/CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025.pdf
...
```

## Comparison with Terminal wget

You're right that this is similar to what you can do with `wget`:

```bash
# Terminal equivalent
wget -qO- https://cofsegovia.com/farmacias-de-guardia/ | grep -o 'href="[^"]*\.pdf"' | sed 's/href="//g' | sed 's/"//g'
```

The Android implementation does the same thing but:
- Runs automatically at app startup
- Associates URLs with region names
- Handles relative URL resolution
- Provides structured data for the app

## Future Enhancements

The scraped URLs can be used to:
1. **Update hardcoded URLs** in `Region.kt` automatically
2. **Detect URL changes** and notify users
3. **Cache URLs** with timestamps to avoid repeated scraping
4. **Validate URLs** before using them for PDF downloads

This lightweight approach gives you the same functionality as Jsoup but with much better performance and zero additional dependencies!
