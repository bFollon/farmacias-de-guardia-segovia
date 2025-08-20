/*
 * Copyright (C) 2025  Bruno Follon (@bFollon)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import Foundation
import PDFKit

/// Base class for parsing PDFs that use a column-based layout.
/// This extracts common functionality that might be shared between different region parsers.
public class ColumnBasedPDFParser {
    /// Represents a column of text in the PDF with its position and content
    struct TextColumn {
        let x: CGFloat
        let width: CGFloat
        var texts: [(y: CGFloat, text: String)]
        
        init(x: CGFloat, width: CGFloat) {
            self.x = x
            self.width = width
            self.texts = []
        }
    }
    
    /// Removes duplicate adjacent text blocks, keeping only the first occurrence
    func removeDuplicateAdjacent(blocks: [(y: CGFloat, text: String)]) -> [(y: CGFloat, text: String)] {
        guard !blocks.isEmpty else { return [] }
        
        var result: [(y: CGFloat, text: String)] = [blocks[0]]
        for i in 1..<blocks.count {
            let current = blocks[i]
            // Only add if text is different from the previous block
            if current.text != result.last?.text {
                result.append(current)
            }
        }
        return result
    }
    
    /// Scans a column in the PDF page and extracts text
    func scanColumn(_ page: PDFPage, column: TextColumn, baseHeight: CGFloat, scanIncrement: CGFloat) -> [(y: CGFloat, text: String)] {
        var texts: [(y: CGFloat, text: String)] = []
        
        // Scan from top to bottom with high precision
        for y in stride(from: 0, to: page.bounds(for: .mediaBox).height, by: scanIncrement) {
            let rect = CGRect(x: column.x, y: y, width: column.width, height: baseHeight)
            if let selection = page.selection(for: rect),
               let text = selection.string?.trimmingCharacters(in: .whitespacesAndNewlines),
               !text.isEmpty {
                texts.append((y: y, text: text))
            }
        }
        
        return texts
    }
    
    /// Gets the smallest font size used in the PDF, useful for determining scan height
    func getSmallestFontSize(from page: PDFPage) -> CGFloat {
        var fontSizes: Set<CGFloat> = []
        
        if let pageContent = page.attributedString {
            pageContent.enumerateAttribute(.font, in: NSRange(location: 0, length: pageContent.length)) { font, _, _ in
                if let ctFont = font as! CTFont? {
                    fontSizes.insert(CTFontGetSize(ctFont))
                }
            }
        }
        
        return fontSizes.min() ?? 5.0 // Default to 5pt if no fonts found
    }
}
