package com.sapjco.mcp.handlers.document;

import com.sapjco.mcp.handlers.AbstractWikiHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.service.FileStorageService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Map;

/**
 * MCP handler for generate_word — generate a Word (.docx) document.
 */
@Slf4j
@Component
public class GenerateWordHandler extends AbstractWikiHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("title", "Document title (rendered as heading 1)")
                .requiredString("content", "Main body text (plain text, newlines become paragraphs)")
                .optionalString("sections", "JSON array of sections: [{\"heading\":\"...\",\"text\":\"...\"}]")
                .optionalString("filename", "Output filename without extension (default: auto-generated)")
                .buildTool("generate_word",
                        "Generate a Microsoft Word (.docx) document from structured content. " +
                        "Creates a formatted document with title, body text, and optional numbered sections. " +
                        "Output saved to /tmp/sap-mcp/documents/ and file path is returned.");
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

        log.info("generate_word: title={} filename={}", title, filename);

        try (XWPFDocument doc = new XWPFDocument()) {
            // Title paragraph (Heading 1 style)
            XWPFParagraph titlePara = doc.createParagraph();
            titlePara.setStyle("Heading1");
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText(title);
            titleRun.setBold(true);
            titleRun.setFontSize(20);

            // Body content
            for (String line : content.split("\n")) {
                XWPFParagraph para = doc.createParagraph();
                XWPFRun run = para.createRun();
                run.setText(line);
                run.setFontSize(11);
            }

            // Sections
            if (sectionsJson != null && sectionsJson.startsWith("[")) {
                String[] entries = sectionsJson.split("\\},\\s*\\{");
                for (String entry : entries) {
                    String heading = extractJsonString(entry, "heading");
                    String text = extractJsonString(entry, "text");

                    if (heading != null) {
                        XWPFParagraph hPara = doc.createParagraph();
                        hPara.setStyle("Heading2");
                        XWPFRun hRun = hPara.createRun();
                        hRun.setText(heading);
                        hRun.setBold(true);
                        hRun.setFontSize(14);
                    }
                    if (text != null) {
                        for (String line : text.split("\n")) {
                            XWPFParagraph para = doc.createParagraph();
                            XWPFRun run = para.createRun();
                            run.setText(line);
                            run.setFontSize(11);
                        }
                    }
                }
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.write(bos);
            byte[] docxBytes = bos.toByteArray();

            Path filePath = fileStorageService.writeBinary("documents", FileStorageService.CAT_DOCUMENTS,
                    filename, docxBytes, FileStorageService.EXT_DOCX);

            return fileResponse(filePath, docxBytes.length, "Word Document Generated: " + title);
        } catch (Exception e) {
            log.error("generate_word failed: {}", title, e);
            return error(e);
        }
    }

    private String extractJsonString(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) return null;
        int start = json.indexOf("\"", idx + key.length() + 2) + 1;
        int end = json.indexOf("\"", start);
        if (start <= 0 || end <= start) return null;
        return json.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
    }
}
