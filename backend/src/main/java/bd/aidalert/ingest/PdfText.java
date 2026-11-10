package bd.aidalert.ingest;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/** PDF to text with Apache PDFBox 3. Works for text PDFs; a scanned PDF gives empty text and needs OCR. */
public final class PdfText {
    private PdfText() {}

    public static String extract(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper s = new PDFTextStripper();
            s.setSortByPosition(true);
            return s.getText(doc);
        }
    }
}
