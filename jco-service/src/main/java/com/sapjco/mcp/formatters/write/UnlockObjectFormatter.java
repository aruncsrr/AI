package com.sapjco.mcp.formatters.write;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Formatter for UnlockObject tool responses.
 * SAP typically returns empty body on success, or error details on failure.
 */
@Slf4j
@Component
public class UnlockObjectFormatter extends AbstractADTFormatter {

    @Override
    public String getToolName() {
        return "UnlockObject";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        StringBuilder sb = new StringBuilder();
        sb.append("Unlock Object Result\n");
        sb.append(boxDivider(60)).append("\n\n");

        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            sb.append("Status: SUCCESS\n\n");
            sb.append("The object was unlocked successfully.\n");
            sb.append("The lock has been released.");
            return sb.toString();
        }

        // Check for error indicators in response
        if (containsError(rawResponse)) {
            sb.append("Status: FAILED\n\n");
            sb.append("Could not unlock the object.\n\n");
            sb.append("Error Details:\n");
            sb.append(parseErrorMessage(rawResponse));
        } else {
            sb.append("Status: SUCCESS\n\n");
            sb.append("The object was unlocked successfully.\n");
            sb.append("The lock has been released.");
        }

        return sb.toString();
    }

    private boolean containsError(String response) {
        return response.contains("<error") ||
               response.contains("type=\"E\"") ||
               response.contains("severity=\"error\"") ||
               response.contains("<exception") ||
               response.contains("not locked") ||
               response.contains("invalid lock");
    }

    private String parseErrorMessage(String response) {
        // Check for "not locked" message
        if (response.contains("not locked")) {
            return "Object is not locked or lock has expired.";
        }

        // Check for "invalid lock" message
        if (response.contains("invalid lock")) {
            return "Invalid lock handle provided.";
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
