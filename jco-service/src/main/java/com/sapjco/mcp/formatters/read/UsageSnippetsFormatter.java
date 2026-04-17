package com.sapjco.mcp.formatters.read;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import com.sapjco.mcp.util.xml.ElementExtractor;
import com.sapjco.mcp.util.xml.FieldExtractor;
import com.sapjco.mcp.util.xml.XmlExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Formatter for GetUsageSnippets tool responses.
 * Converts raw usage snippets XML into human-readable code context listing.
 */
@Slf4j
@Component
public class UsageSnippetsFormatter extends AbstractADTFormatter {

    private static final String ADT_NS = "http://www.sap.com/adt/core";

    private static final ElementExtractor CODE_SNIPPET_EXTRACTOR = ElementExtractor.builder("codeSnippet")
            .field(FieldExtractor.builder("uri")
                    .fromNamespacedAttribute(ADT_NS, "uri")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("line")
                    .fromChildAttribute("match", "line")
                    .asInteger()
                    .defaultValue(0)
                    .build())
            .field(FieldExtractor.builder("column")
                    .fromChildAttribute("match", "column")
                    .asInteger()
                    .defaultValue(0)
                    .build())
            .field(FieldExtractor.builder("content")
                    .fromChildText("content")
                    .asString()
                    .build())
            .build();

    @Override
    public String getToolName() {
        return "GetUsageSnippets";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        List<CodeSnippet> snippets = parseUsageSnippets(rawResponse);
        return formatUsageSnippets(snippets);
    }

    /**
     * Parsed code snippet from usage results.
     */
    private static class CodeSnippet {
        final String uri;
        final int line;
        final int column;
        final String content;

        CodeSnippet(String uri, int line, int column, String content) {
            this.uri = uri;
            this.line = line;
            this.column = column;
            this.content = content;
        }
    }

    private List<CodeSnippet> parseUsageSnippets(String xml) {
        return XmlExtractor.extract(xml, CODE_SNIPPET_EXTRACTOR)
                .stream()
                .map(this::toCodeSnippet)
                .collect(Collectors.toList());
    }

    private CodeSnippet toCodeSnippet(Map<String, Object> data) {
        return new CodeSnippet(
                (String) data.get("uri"),
                (Integer) data.get("line"),
                (Integer) data.get("column"),
                (String) data.get("content")
        );
    }

    private String formatUsageSnippets(List<CodeSnippet> snippets) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Usage Snippets: %d location%s\n",
                snippets.size(), snippets.size() == 1 ? "" : "s"));
        sb.append(boxDivider(80)).append("\n\n");

        if (snippets.isEmpty()) {
            sb.append("No code snippets found.\n");
            return sb.toString();
        }

        for (int i = 0; i < snippets.size(); i++) {
            CodeSnippet snippet = snippets.get(i);

            // Extract filename from URI
            String filename = extractFilename(snippet.uri);
            sb.append(String.format("[%s]\n", filename));
            sb.append(String.format("   Line %d, Column %d:\n", snippet.line, snippet.column));

            // Format code with line numbers
            if (snippet.content != null && !snippet.content.isEmpty()) {
                sb.append("   +").append(boxDivider(60)).append("\n");
                String[] lines = snippet.content.split("\n");
                int startLine = Math.max(1, snippet.line - 2);
                for (int j = 0; j < lines.length; j++) {
                    int lineNum = startLine + j;
                    String marker = lineNum == snippet.line ? "|>" : "| ";
                    sb.append(String.format("   %s %3d: %s\n", marker, lineNum, lines[j]));
                }
                sb.append("   +").append(boxDivider(60)).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String extractFilename(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "(unknown)";
        }
        // Remove query params and fragments
        String path = uri.split("[?#]")[0];
        // Get last meaningful segment
        String[] parts = path.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isEmpty() && !parts[i].equals("source") && !parts[i].equals("main")) {
                return parts[i];
            }
        }
        return path;
    }
}
