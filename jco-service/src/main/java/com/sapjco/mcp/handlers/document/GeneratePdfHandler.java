package com.sapjco.mcp.handlers.document;

import com.sapjco.mcp.handlers.AbstractWikiHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.service.FileStorageService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Map;

/**
 * MCP handler for generate_pdf — generate a PDF document from title + content sections.
 */
@Slf4j
@Component
public class GeneratePdfHandler extends AbstractWikiHandler {

    private static final float MARGIN = 50f;
    private static final float TITLE_FONT_SIZE = 20f;
    private static final float HEADING_FONT_SIZE = 14f;
    private static final float BODY_FONT_SIZE = 11f;
    private static final float LINE_SPACING = 14f;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("title", "Document title")
                .requiredString("content", "Main body text (plain text, newlines supported)")
                .optionalString("sections", "JSON array of sections: [{\"heading\":\"...\",\"text\":\"...\"}]")
                .optionalString("filename", "Output filename without extension (default: auto-generated)")
                .buildTool("generate_pdf",
                        "Generate a PDF document from structured content. " +
                        "Produces a formatted PDF with title, optional sections, and body text. " +
                        "Output is saved to /tmp/sap-mcp/documents/ and the file path is returned.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        CallToolResult validation = validateRequired(args, "title");
        if (validation != null) return validation;
        validation = validateRequired(args, "content");
        if (validation != null) return validation;

        String title = requireString(args, "title");
        String content = requireString(args, "content");
        String sectionsJson = optionalString(args, "sections");
        String filename = optionalString(args, "filename",
                "document_" + System.currentTimeMillis());

        log.info("generate_pdf: title={} filename={}", title, filename);

        try (PDDocument document = new PDDocument()) {
            PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float pageWidth = page.getMediaBox().getWidth();
            float textWidth = pageWidth - 2 * MARGIN;
            float y = page.getMediaBox().getHeight() - MARGIN;

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                // Title
                cs.beginText();
                cs.setFont(boldFont, TITLE_FONT_SIZE);
                cs.newLineAtOffset(MARGIN, y);
                cs.showText(truncate(title, 80));
                cs.endText();
                y -= TITLE_FONT_SIZE + 10;

                // Divider line
                cs.moveTo(MARGIN, y);
                cs.lineTo(pageWidth - MARGIN, y);
                cs.stroke();
                y -= 15;

                // Main content
                y = writeWrappedText(cs, regularFont, BODY_FONT_SIZE, content, MARGIN, y, textWidth, document, page);
            }

            // Sections (if provided)
            if (sectionsJson != null && sectionsJson.startsWith("[")) {
                y = writeSectionsFromJson(document, boldFont, regularFont, sectionsJson, y, textWidth);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            document.save(bos);
            byte[] pdfBytes = bos.toByteArray();

            Path filePath = fileStorageService.writeBinary("documents", FileStorageService.CAT_DOCUMENTS,
                    filename, pdfBytes, FileStorageService.EXT_PDF);

            return fileResponse(filePath, pdfBytes.length,
                    "PDF Generated: " + title,
                    "File: " + filePath);
        } catch (Exception e) {
            log.error("generate_pdf failed: {}", title, e);
            return error(e);
        }
    }

    private float writeWrappedText(PDPageContentStream cs, PDType1Font font, float fontSize,
                                    String text, float x, float y, float maxWidth,
                                    PDDocument doc, PDPage page) throws Exception {
        if (text == null || text.isBlank()) return y;
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (y < MARGIN + 20) break; // simple overflow guard
            cs.beginText();
            cs.setFont(font, fontSize);
            cs.newLineAtOffset(x, y);
            cs.showText(truncate(line, 120));
            cs.endText();
            y -= LINE_SPACING;
        }
        return y;
    }

    private float writeSectionsFromJson(PDDocument document, PDType1Font boldFont, PDType1Font regularFont,
                                         String json, float startY, float textWidth) {
        // Simple JSON parsing for [{heading,text}] without external library
        float y = startY;
        String[] entries = json.split("\\},\\s*\\{");
        for (String entry : entries) {
            String heading = extractJsonString(entry, "heading");
            String text = extractJsonString(entry, "text");
            if (heading == null && text == null) continue;

            try {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                y = page.getMediaBox().getHeight() - MARGIN;

                try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                    if (heading != null) {
                        cs.beginText();
                        cs.setFont(boldFont, HEADING_FONT_SIZE);
                        cs.newLineAtOffset(MARGIN, y);
                        cs.showText(truncate(heading, 90));
                        cs.endText();
                        y -= HEADING_FONT_SIZE + 8;
                    }
                    if (text != null) {
                        y = writeWrappedText(cs, regularFont, BODY_FONT_SIZE, text,
                                MARGIN, y, textWidth, document, page);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return y;
    }

    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"";
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) return null;
        int start = json.indexOf("\"", idx + key.length() + 2) + 1;
        int end = json.indexOf("\"", start);
        if (start <= 0 || end <= start) return null;
        return json.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
