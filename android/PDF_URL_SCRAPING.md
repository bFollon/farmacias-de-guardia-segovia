# PDF URL Scraping Feature

This document describes the PDF URL scraping feature that prevents source URLs from becoming stale.

## Overview

The app now automatically scrapes the stable page at `https://cofsegovia.com/farmacias-de-guardia/` at startup to extract the latest PDF URLs for all regions. This ensures that the app always uses the most current PDF links even if they change on the server.

## Implementation

### Components

1. **PDFURLScrapingService** - Core service that handles web scraping
2. **PDFURLScrapingDemo** - Demo utility for testing the scraping functionality
3. **SplashViewModel** - Integrates scraping into app startup
4. **PDFURLScrapingServiceTest** - Unit tests for the scraping functionality

### How It Works

1. **App Startup**: When the app starts, the `SplashViewModel` triggers PDF URL scraping
2. **Web Scraping**: The service connects to `cofsegovia.com/farmacias-de-guardia/` and parses the HTML
3. **URL Extraction**: It looks for PDF links and associates them with region names
4. **Console Output**: The scraped URLs are printed to the console for debugging
5. **Future Integration**: The scraped URLs can be used to update the hardcoded URLs in `Region.kt`

### Dependencies Used

- **OkHttp 5.1.0** - Already present in project for HTTP requests
- **Regex patterns** - Built-in Kotlin regex for HTML parsing (no additional dependencies!)
- **Internet Permission** - Already present in AndroidManifest.xml

## Usage

### Automatic Scraping

The scraping happens automatically at app startup. You can see the results in the Android logcat:

```
D/PDFURLScrapingService: Starting PDF URL scraping from https://cofsegovia.com/farmacias-de-guardia/
D/PDFURLScrapingService: Successfully fetched HTML content (12345 chars)
D/PDFURLScrapingService: Found 8 PDF links in HTML
D/PDFURLScrapingService: After removing duplicates: 4 unique PDF URLs
D/PDFURLScrapingService: Successfully scraped 4 PDF URLs
D/PDFURLScrapingService: Found PDF for Segovia Capital: https://cofsegovia.com/wp-content/uploads/2025/05/CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025.pdf
D/PDFURLScrapingService: Found PDF for Cuéllar: https://cofsegovia.com/wp-content/uploads/2025/01/GUARDIAS-CUELLAR_2025.pdf
...
```

### Manual Testing

You can run the scraping demo manually by calling:

```kotlin
// In your ViewModel or Activity
splashViewModel.runScrapingDemo()
```

### Unit Testing

Run the unit tests to verify the scraping functionality:

```bash
cd android
./gradlew test
```

## Configuration

### Scraping Parameters

The scraping service uses these default parameters:

- **URL**: `https://cofsegovia.com/farmacias-de-guardia/`
- **Timeout**: 10 seconds
- **User Agent**: Mozilla/5.0 (Android; Mobile; rv:13.0) Gecko/13.0 Firefox/13.0
- **Method**: OkHttp + Regex patterns (much lighter than Jsoup!)

### Region Detection

The service looks for these region keywords in the HTML:

- "segovia" + "capital" → Segovia Capital
- "cuellar" or "cuéllar" → Cuéllar  
- "espinar" or "san rafael" → El Espinar
- "rural" → Segovia Rural

## Future Enhancements

### URL Update Integration

The scraped URLs can be used to automatically update the hardcoded URLs in `Region.kt`:

```kotlin
// Example future implementation
fun updateRegionURLsFromScraping() {
    val scrapedData = getScrapedPDFURLs()
    scrapedData.forEach { data ->
        // Update the corresponding region's PDF URL
        // This would require modifying the Region data class
    }
}
```

### Caching

Consider adding caching for scraped URLs to avoid repeated network requests:

```kotlin
// Store scraped URLs with timestamps
// Only re-scrape if cache is older than X hours
```

### Error Handling

The current implementation includes basic error handling, but could be enhanced with:

- Retry logic for failed requests
- Fallback to hardcoded URLs if scraping fails
- User notification of URL updates

## Troubleshooting

### Common Issues

1. **No PDFs Found**: Check if the website structure has changed
2. **Network Errors**: Verify internet connectivity and permissions
3. **Parsing Errors**: The HTML structure might have changed

### Debug Information

Enable debug logging to see detailed scraping information:

```kotlin
// The service already uses DebugConfig.debugPrint() for logging
// Check logcat for "PDFURLScrapingService" tags
```

## Testing

### Manual Testing

1. Run the app and check logcat for scraping output
2. Use the demo function to test scraping independently
3. Verify that scraped URLs match expected patterns

### Automated Testing

1. Run unit tests: `./gradlew test`
2. Check that tests pass and URLs are extracted correctly
3. Verify error handling with network failures

## Security Considerations

- The scraping only reads public information from the website
- No sensitive data is transmitted or stored
- Uses standard HTTP requests with appropriate user agent
- Respects website's robots.txt (if present)
