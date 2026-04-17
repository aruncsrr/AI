package com.sapjco.mcp.handlers.document;

import com.sapjco.mcp.handlers.AbstractWikiHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.service.FileStorageService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Map;

/**
 * MCP handler for generate_pptx — generate a PowerPoint presentation.
 */
@Slf4j
@Component
public class GeneratePptxHandler extends AbstractWikiHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("title", "Presentation title (shown on the title slide)")
                .requiredString("slides", "JSON array of slides: [{\"title\":\"...\",\"content\":\"...\"}]. " +
                        "Use \\n in content for bullet points.")
                .optionalString("filename", "Output filename without extension (default: auto-generated)")
                .buildTool("generate_pptx",
                        "Generate a PowerPoint (.pptx) presentation. " +
                        "Creates a title slide followed by content slides. " +
                        "Content lines become bullet points on each slide. " +
                        "Output saved to /tmp/sap-mcp/documents/ and file path is returned.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        CallToolResult validation = validateRequired(args, "title");
        if (validation != null) return validation;
        validation = validateRequired(args, "slides");
        if (validation != null) return validation;

        String presentationTitle = requireString(args, "title");
        String slidesJson = requireString(args, "slides");
        String filename = optionalString(args, "filename",
                "presentation_" + System.currentTimeMillis());

        log.info("generate_pptx: title={} filename={}", presentationTitle, filename);

        try (XMLSlideShow pptx = new XMLSlideShow()) {
            // Title slide
            XSLFSlideLayout titleLayout = pptx.getSlideMasters().get(0)
                    .getLayout(SlideLayout.TITLE);
            XSLFSlide titleSlide = pptx.createSlide(titleLayout);
            XSLFTextShape titleShape = titleSlide.getPlaceholder(0);
            if (titleShape != null) {
                titleShape.clearText();
                XSLFTextParagraph p = titleShape.addNewTextParagraph();
                XSLFTextRun run = p.addNewTextRun();
                run.setText(presentationTitle);
                run.setFontSize(36.0);
                run.setBold(true);
            }

            // Content slides from JSON
            String[] entries = slidesJson.split("\\},\\s*\\{");
            int slideCount = 0;
            for (String entry : entries) {
                String slideTitle = extractJsonString(entry, "title");
                String slideContent = extractJsonString(entry, "content");
                if (slideTitle == null && slideContent == null) continue;

                XSLFSlideLayout contentLayout = pptx.getSlideMasters().get(0)
                        .getLayout(SlideLayout.TITLE_AND_CONTENT);
                XSLFSlide slide = pptx.createSlide(contentLayout);

                // Slide title
                XSLFTextShape titleBox = slide.getPlaceholder(0);
                if (titleBox != null && slideTitle != null) {
                    titleBox.clearText();
                    XSLFTextParagraph tp = titleBox.addNewTextParagraph();
                    XSLFTextRun tr = tp.addNewTextRun();
                    tr.setText(slideTitle);
                    tr.setFontSize(24.0);
                    tr.setBold(true);
                    tr.setFontColor(Color.decode("#003366"));
                }

                // Slide content as bullet points
                XSLFTextShape contentBox = slide.getPlaceholder(1);
                if (contentBox != null && slideContent != null) {
                    contentBox.clearText();
                    String[] bullets = slideContent.split("\n");
                    for (String bullet : bullets) {
                        if (bullet.isBlank()) continue;
                        XSLFTextParagraph cp = contentBox.addNewTextParagraph();
                        cp.setIndentLevel(0);
                        XSLFTextRun cr = cp.addNewTextRun();
                        cr.setText(bullet.trim());
                        cr.setFontSize(16.0);
                    }
                }
                slideCount++;
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            pptx.write(bos);
            byte[] pptxBytes = bos.toByteArray();

            Path filePath = fileStorageService.writeBinary("documents", FileStorageService.CAT_DOCUMENTS,
                    filename, pptxBytes, FileStorageService.EXT_PPTX);

            return fileResponse(filePath, pptxBytes.length,
                    "PPTX Generated: " + presentationTitle,
                    "Slides: " + (slideCount + 1) + " (1 title + " + slideCount + " content)");
        } catch (Exception e) {
            log.error("generate_pptx failed: {}", presentationTitle, e);
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
