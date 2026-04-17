package com.sapjco.mcp.formatters.write;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formatter for LockObject tool responses.
 * Parses lock response XML to extract lock handle and transport information.
 */
@Slf4j
@Component
public class LockObjectFormatter extends AbstractADTFormatter {

    // Pattern to extract lock handle from response
    private static final Pattern LOCK_HANDLE_PATTERN =
            Pattern.compile("LOCK_HANDLE=([^&\\s\"<>]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRANSPORT_PATTERN =
            Pattern.compile("CORRNR=([A-Z0-9]{10,})", Pattern.CASE_INSENSITIVE);

    @Override
    public String getToolName() {
        return "LockObject";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw FormattingException.emptyResponse(getToolName());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Lock Object Result\n");
        sb.append(boxDivider(60)).append("\n\n");

        String lockHandle = null;
        String transport = null;

        // Try to parse as XML
        try {
            Document doc = parseXml(rawResponse);
            lockHandle = extractLockHandleFromXml(doc);
            transport = extractTransportFromXml(doc);
        } catch (FormattingException e) {
            log.debug("Could not parse lock response as XML: {}", e.getMessage());
        }

        // Fallback: extract from raw text using patterns
        if (lockHandle == null) {
            lockHandle = extractFromPattern(rawResponse, LOCK_HANDLE_PATTERN);
        }
        if (transport == null) {
            transport = extractFromPattern(rawResponse, TRANSPORT_PATTERN);
        }

        // Check for error
        if (containsError(rawResponse)) {
            sb.append("Status: FAILED\n\n");
            sb.append("Could not lock the object.\n\n");
            sb.append("Error Details:\n");
            sb.append(parseErrorMessage(rawResponse));
            return sb.toString();
        }

        sb.append("Status: SUCCESS\n\n");
        sb.append("Object locked successfully.\n\n");

        if (lockHandle != null && !lockHandle.isEmpty()) {
            sb.append(String.format("Lock Handle: %s\n", lockHandle));
        }
        if (transport != null && !transport.isEmpty()) {
            sb.append(String.format("Transport: %s\n", transport));
        }

        sb.append("\nRemember to unlock the object when done using UnlockObject,\n");
        sb.append("or use SaveClass which handles locking automatically.");

        return sb.toString();
    }

    private String extractLockHandleFromXml(Document doc) {
        // Try common element/attribute names for lock handle
        List<Element> lockElements = getElementsByTagName(doc, "lock");
        for (Element elem : lockElements) {
            String handle = getAttribute(elem, "lockHandle");
            if (!handle.isEmpty()) {
                return handle;
            }
            handle = getAttribute(elem, "LOCK_HANDLE");
            if (!handle.isEmpty()) {
                return handle;
            }
        }

        // Try objectLock element
        List<Element> objectLocks = getElementsByTagName(doc, "objectLock");
        for (Element elem : objectLocks) {
            String handle = getAttribute(elem, "lockHandle");
            if (!handle.isEmpty()) {
                return handle;
            }
        }

        // Try to find lock handle in any attribute
        String docString = doc.getDocumentElement().toString();
        return extractFromPattern(docString, LOCK_HANDLE_PATTERN);
    }

    private String extractTransportFromXml(Document doc) {
        // Try transport elements
        for (String tagName : new String[]{"transport", "corrNr", "transportRequest"}) {
            List<Element> elements = getElementsByTagName(doc, tagName);
            for (Element elem : elements) {
                String content = elem.getTextContent().trim();
                if (!content.isEmpty() && content.matches("[A-Z0-9]{10,}")) {
                    return content;
                }
                String nr = getAttribute(elem, "corrNr");
                if (!nr.isEmpty()) {
                    return nr;
                }
            }
        }
        return null;
    }

    private String extractFromPattern(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private boolean containsError(String response) {
        return response.contains("<error") ||
               response.contains("type=\"E\"") ||
               response.contains("severity=\"error\"") ||
               response.contains("<exception") ||
               response.contains("locked by");
    }

    private String parseErrorMessage(String response) {
        // Check for "locked by" message (object already locked)
        if (response.contains("locked by")) {
            int start = response.indexOf("locked by");
            int end = response.indexOf("</", start);
            if (end > start) {
                return "Object is already " + response.substring(start, end);
            }
            return "Object is already locked by another user.";
        }

        // Try to extract message element
        if (response.contains("<message>")) {
            int start = response.indexOf("<message>") + 9;
            int end = response.indexOf("</message>", start);
            if (end > start) {
                return response.substring(start, end);
            }
        }
        return response;
    }
}
