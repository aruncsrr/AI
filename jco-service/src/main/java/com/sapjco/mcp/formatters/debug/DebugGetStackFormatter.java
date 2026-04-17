package com.sapjco.mcp.formatters.debug;

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
 * Formatter for DebugGetStack tool responses.
 * Formats call stack with source locations.
 */
@Slf4j
@Component
public class DebugGetStackFormatter extends AbstractADTFormatter {

    private static final String ADT_NS = "http://www.sap.com/adt/core";

    // Stack entry extractor
    private static final ElementExtractor STACK_ENTRY_EXTRACTOR = ElementExtractor.builder("stackEntry")
            .filter(elem -> !"DYNP".equals(elem.getAttribute("stackType")))
            .field(FieldExtractor.builder("position")
                    .fromAttribute("stackPosition")
                    .asInteger()
                    .defaultValue(0)
                    .build())
            .field(FieldExtractor.builder("programName")
                    .fromAttribute("programName")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("includeName")
                    .fromAttribute("includeName")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("line")
                    .fromAttribute("line")
                    .asInteger()
                    .defaultValue(0)
                    .build())
            .field(FieldExtractor.builder("eventType")
                    .fromAttribute("eventType")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("eventName")
                    .fromAttribute("eventName")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("uri")
                    .fromNamespacedAttribute(ADT_NS, "uri")
                    .asString()
                    .build())
            .field(FieldExtractor.builder("isActive")
                    .fromAttribute("isActive")
                    .asBoolean()
                    .defaultValue(false)
                    .build())
            .field(FieldExtractor.builder("isSystemProgram")
                    .fromAttribute("systemProgram")
                    .asBoolean()
                    .defaultValue(false)
                    .build())
            .build();

    /**
     * Stack frame data class.
     */
    private static class StackFrame {
        final int position;
        final String programName;
        final String includeName;
        final int line;
        final String eventType;
        final String eventName;
        final String uri;
        final boolean isActive;
        final boolean isSystemProgram;

        StackFrame(int position, String programName, String includeName, int line,
                   String eventType, String eventName, String uri, boolean isActive,
                   boolean isSystemProgram) {
            this.position = position;
            this.programName = programName;
            this.includeName = includeName;
            this.line = line;
            this.eventType = eventType;
            this.eventName = eventName;
            this.uri = uri;
            this.isActive = isActive;
            this.isSystemProgram = isSystemProgram;
        }
    }

    @Override
    public String getToolName() {
        return "DebugGetStack";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        // Check if this is already formatted text (from handler)
        if (rawResponse.contains("Current Position:") || rawResponse.contains("Call Stack (")) {
            return formatTextResponse(rawResponse);
        }

        // Parse XML response
        List<StackFrame> frames = parseStackTrace(rawResponse);
        return formatStackFrames(frames);
    }

    private String formatTextResponse(String rawResponse) {
        // Response is already formatted, just clean it up
        StringBuilder sb = new StringBuilder();
        sb.append("Debug Call Stack\n");
        sb.append(boxDivider(80)).append("\n\n");
        sb.append(rawResponse.trim());
        return sb.toString();
    }

    private String formatStackFrames(List<StackFrame> frames) {
        StringBuilder sb = new StringBuilder();
        sb.append("Debug Call Stack\n");
        sb.append(boxDivider(80)).append("\n\n");

        // Find and show current position (active frame)
        StackFrame activeFrame = frames.stream()
                .filter(f -> f.isActive)
                .findFirst()
                .orElse(null);

        if (activeFrame != null) {
            sb.append("Current Position\n");
            sb.append(divider(40)).append("\n");
            sb.append(String.format("  Program:  %s\n", extractClassName(activeFrame.programName)));
            sb.append(String.format("  Include:  %s\n", activeFrame.includeName));
            sb.append(String.format("  Method:   %s\n", activeFrame.eventName));
            sb.append(String.format("  Line:     %d\n", activeFrame.line));
            if (activeFrame.uri != null && !activeFrame.uri.isEmpty()) {
                sb.append(String.format("  URI:      %s\n", activeFrame.uri));
            }
            sb.append("\n");
        }

        // Show call stack table
        sb.append(String.format("Call Stack (%d frames)\n", frames.size()));
        sb.append(boxDivider(80)).append("\n");
        sb.append(String.format("%-4s %-35s %s\n", "#", "Method/Event", "Location"));
        sb.append(boxDivider(80)).append("\n");

        int maxFrames = Math.min(frames.size(), 15);
        for (int i = 0; i < maxFrames; i++) {
            StackFrame frame = frames.get(i);
            String marker = frame.isActive ? ">" : " ";

            String eventName = frame.eventName.length() > 33
                    ? frame.eventName.substring(0, 30) + "..."
                    : frame.eventName;

            String location = String.format("%s:%d", extractClassName(frame.programName), frame.line);

            sb.append(String.format("%s%-3d %-35s %s\n",
                    marker, frame.position, eventName, location));
        }

        if (frames.size() > maxFrames) {
            sb.append(String.format("\n  ... and %d more frames\n", frames.size() - maxFrames));
        }

        return sb.toString();
    }

    private String extractClassName(String programName) {
        if (programName == null) return "";
        // Remove padding (= signs) from program names like "ZTEST_CLASS=================CP"
        return programName.replaceAll("=+.*$", "");
    }

    private List<StackFrame> parseStackTrace(String xml) {
        return XmlExtractor.extract(xml, STACK_ENTRY_EXTRACTOR)
                .stream()
                .map(this::toStackFrame)
                .collect(Collectors.toList());
    }

    private StackFrame toStackFrame(Map<String, Object> data) {
        return new StackFrame(
                data.get("position") != null ? (Integer) data.get("position") : 0,
                (String) data.get("programName"),
                (String) data.get("includeName"),
                data.get("line") != null ? (Integer) data.get("line") : 0,
                (String) data.get("eventType"),
                (String) data.get("eventName"),
                (String) data.get("uri"),
                data.get("isActive") != null ? (Boolean) data.get("isActive") : false,
                data.get("isSystemProgram") != null ? (Boolean) data.get("isSystemProgram") : false
        );
    }
}
