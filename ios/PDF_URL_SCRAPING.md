# PDF URL Scraping Feature - iOS

This document describes the PDF URL scraping feature for the iOS app that prevents source URLs from becoming stale.

## Overview

The iOS app now automatically scrapes the stable page at `https://cofsegovia.com/farmacias-de-guardia/` at startup to extract the latest PDF URLs for all regions. This ensures that the app always uses the most current PDF links even if they change on the server.

## Implementation

### Components

1. **PDFURLScrapingService** - Core service that handles web scraping
2. **PDFURLScrapingDemo** - Demo utility for testing the scraping functionality
3. **PreloadService** - Integrates scraping into app startup

### How It Works

1. **App Startup**: When the app starts, the `PreloadService` triggers PDF URL scraping
2. **Web Scraping**: The service connects to `cofsegovia.com/farmacias-de-guardia/` and parses the HTML
3. **URL Extraction**: It looks for PDF links and associates them with region names
4. **Console Output**: The scraped URLs are printed to the console for debugging
5. **Future Integration**: The scraped URLs can be used to update the hardcoded URLs in `Region.swift`

### Dependencies Used

- **URLSession** - Built-in iOS networking framework
- **NSRegularExpression** - Built-in regex support for HTML parsing
- **Foundation** - Standard iOS framework (no additional dependencies!)

## Usage

### Automatic Scraping

The scraping happens automatically at app startup. You can see the results in the Xcode console:

```
[DEBUG] PreloadService: Starting PDF URL scraping...
[DEBUG] PDFURLScrapingService: Starting PDF URL scraping from https://cofsegovia.com/farmacias-de-guardia/
[DEBUG] PDFURLScrapingService: Successfully fetched HTML content (12345 chars)
[DEBUG] PDFURLScrapingService: Found 8 PDF links in HTML
[DEBUG] PDFURLScrapingService: After removing duplicates: 4 unique PDF URLs
[DEBUG] PDFURLScrapingService: Successfully scraped 4 PDF URLs
[DEBUG] PDFURLScrapingService: Found PDF for Segovia Capital: https://cofsegovia.com/wp-content/uploads/2025/05/CALENDARIO-GUARDIAS-SEGOVIA-CAPITAL-DIA-2025.pdf
[DEBUG] PDFURLScrapingService: Found PDF for Cuéllar: https://cofsegovia.com/wp-content/uploads/2025/01/GUARDIAS-CUELLAR_2025.pdf
...
```

### Manual Testing

You can run the scraping demo manually by calling:

```swift
// In your ViewModel or View
await PDFURLScrapingDemo.runDemo()
```

### Unit Testing

Run the unit tests to verify the scraping functionality:

```bash
xcodebuild test -scheme FarmaciasDeGuardiaEnSegovia -destination 'platform=iOS Simulator,name=iPhone 16 Pro'
```

## Configuration

### Scraping Parameters

The scraping service uses these default parameters:

- **URL**: `https://cofsegovia.com/farmacias-de-guardia/`
- **Timeout**: 10 seconds
- **User Agent**: Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15
- **Method**: URLSession + NSRegularExpression (lightweight and native!)

### Region Detection

The service looks for these region keywords in the HTML:

- "segovia" + "capital" → Segovia Capital
- "cuellar" or "cuéllar" → Cuéllar  
- "espinar" or "san rafael" → El Espinar
- "rural" → Segovia Rural

## Key Benefits

- ✅ **Zero additional dependencies** - Uses built-in iOS frameworks
- ✅ **Lightweight** - No external libraries or heavy parsing
- ✅ **Fast** - Simple regex is much faster than DOM traversal
- ✅ **Reliable** - Less prone to HTML structure changes
- ✅ **Maintainable** - Easy to understand and modify
- ✅ **Native** - Uses standard iOS patterns and APIs

## Regex Patterns Used

```swift
// Find PDF links
let pattern = #"href="([^"]*\.pdf)""#

// Find update dates
let regex = try NSRegularExpression(pattern: "\(pattern)[^\\d]*(\\d{1,2}[^\\d]*\\d{4})", options: [.caseInsensitive])
```

## Testing

### Manual Testing

1. Run the app and check Xcode console for scraping output
2. Use the demo function to test scraping independently
3. Verify that scraped URLs match expected patterns

### Automated Testing

1. Run unit tests: `xcodebuild test`
2. Check that tests pass and URLs are extracted correctly
3. Verify error handling with network failures

## Future Enhancements

### URL Update Integration

The scraped URLs can be used to automatically update the hardcoded URLs in `Region.swift`:

```swift
// Example future implementation
func updateRegionURLsFromScraping() async {
    let scrapedData = await PDFURLScrapingService.shared.scrapePDFURLs()
    for data in scrapedData {
        // Update the corresponding region's PDF URL
        // This would require modifying the Region data class
    }
}
```

### Caching

Consider adding caching for scraped URLs to avoid repeated network requests:

```swift
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

```swift
// The service already uses DebugConfig.debugPrint() for logging
// Check Xcode console for "PDFURLScrapingService" messages
```

## Security Considerations

- The scraping only reads public information from the website
- No sensitive data is transmitted or stored
- Uses standard HTTP requests with appropriate user agent
- Respects website's robots.txt (if present)
- Uses iOS's built-in security features

## Comparison with Android

Both iOS and Android implementations use the same approach:
- **Lightweight**: No external dependencies
- **Simple**: Regex-based parsing
- **Fast**: Minimal overhead
- **Reliable**: Less prone to HTML changes

The main differences are:
- **iOS**: Uses URLSession + NSRegularExpression
- **Android**: Uses OkHttp + Kotlin Regex

Both achieve the same goal with platform-native solutions!
