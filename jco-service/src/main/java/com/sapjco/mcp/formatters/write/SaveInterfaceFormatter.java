package com.sapjco.mcp.formatters.write;

import com.sapjco.mcp.formatters.AbstractADTFormatter;
import com.sapjco.mcp.formatters.FormattingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Formatter for SaveInterface tool responses.
 * SAP typically returns empty body on success, or error details on failure.
 */
@Slf4j
@Component
public class SaveInterfaceFormatter extends AbstractADTFormatter {

    @Override
    public String getToolName() {
        return "SaveInterface";
    }

    @Override
    public String format(String rawResponse) throws FormattingException {
        StringBuilder sb = new StringBuilder();
        sb.append("Save Interface Result\n");
        sb.append(boxDivider(60)).append("\n\n");

        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            sb.append("Status: SUCCESS\n\n");
            sb.append("The interface was saved successfully.\n");
            sb.append("Note: The interface is saved but NOT activated.\n");
            sb.append("Use ActivateObject to activate the changes.");
            return sb.toString();
        }

        // Check for error indicators in XML response
        if (containsError(rawResponse)) {
            sb.append("Status: FAILED\n\n");
            sb.append("Error Details:\n");
            sb.append(parseErrorMessage(rawResponse));
        } else {
            sb.append("Status: SUCCESS\n\n");
            sb.append("The interface was saved successfully.\n");
            sb.append("Note: The interface is saved but NOT activated.\n");
            sb.append("Use ActivateObject to activate the changes.");
        }

        return sb.toString();
    }

    private boolean containsError(String response) {
        return response.contains("<error") ||
               response.contains("type=\"E\"") ||
               response.contains("severity=\"error\"") ||
               response.contains("<exception");
    }

    private String parseErrorMessage(String response) {
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
