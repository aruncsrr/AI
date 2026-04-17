package com.sapjco.mcp.handlers.data;

import com.sapjco.mcp.handlers.AbstractReadHandler;
import com.sapjco.mcp.mcp.McpResponseFormatter;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/**
 * MCP handler for CompareCddHeader.
 * Fetches two records from /HEC1/R_HEADER_CDD by key fields,
 * compares all fields side by side, and generates a PDF report.
 */
@Slf4j
@Component
public class CompareCddHeaderHandler extends AbstractReadHandler {

    private static final String CDS_VIEW = "/HEC1/R_HEADER_CDD";
    private static final int FETCH_ROWS = 10000;

    /** Column name (dataPreview:name) used as key fields */
    private static final String COL_CONFID   = "HECCONFID";
    private static final String COL_CONFVER  = "HECCONFVERSION";
    private static final String COL_CDDVER   = "CDDVERSION";

    /** Ordered list of fields to compare: [displayName, xmlColumnName] */
    private static final List<String[]> COMPARE_FIELDS = List.of(
            new String[]{"RequestType",          "REQUESTTYPE"},
            new String[]{"RequestedByUname",     "REQUESTEDBYUNAME"},
            new String[]{"RequestedOnTs",        "REQUESTEDONTS"},
            new String[]{"ExpectedDelDate",      "EXPECTEDDELDATE"},
            new String[]{"BuildStartDate",       "BUILDSTARTDATE"},
            new String[]{"CommittedDelDate",     "COMMITTEDDELDATE"},
            new String[]{"PlannedSignatureDate", "PLANNEDSIGNATUREDATE"},
            new String[]{"CddExpiredDate",       "CDDEXPIREDDATE"},
            new String[]{"HwbuildProvDate",      "HWBUILDPROVDATE"},
            new String[]{"ExpectedS2dDate",      "EXPECTEDS2DDATE"},
            new String[]{"CddStatus",            "CDDSTATUS"},
            new String[]{"ConfigType",           "CONFIGTYPE"},
            new String[]{"XdrApprovalNeeded",    "XDRAPPROVALNEEDED"},
            new String[]{"XnewCustomer",         "XNEWCUSTOMER"},
            new String[]{"RejectIntReason",      "REJECTINTREASON"},
            new String[]{"RejectExtReason",      "REJECTEXTREASON"},
            new String[]{"CrCounter",            "CRCOUNTER"},
            new String[]{"LiveEnvCrValDate",     "LIVEENVCRVALDATE"},
            new String[]{"XteamGdo",             "XTEAMGDO"},
            new String[]{"XteamDr",              "XTEAMDR"},
            new String[]{"XteamCr",              "XTEAMCR"},
            new String[]{"CurrentTeam",          "CURRENTTEAM"},
            new String[]{"IbpTimestamp",         "IBPTIMESTAMP"},
            new String[]{"XibpHwReqCreated",     "XIBPHWREQCREATED"},
            new String[]{"SubconCode",           "SUBCONCODE"},
            new String[]{"CreatedBy",            "CREATEDBY"},
            new String[]{"CreatedAt",            "CREATEDAT"},
            new String[]{"new_cdd",              "NEW_CDD"}
    );

    // ── PDF layout constants ────────────────────────────────────────────────
    private static final float MARGIN       = 40f;
    private static final float TITLE_SIZE   = 16f;
    private static final float HEAD_SIZE    = 10f;
    private static final float BODY_SIZE    = 8.5f;
    private static final float ROW_H        = 12f;
    private static final float COL1_W       = 140f;   // Field name
    private static final float COL2_W       = 150f;   // Value 1
    private static final float COL3_W       = 150f;   // Value 2
    private static final float COL4_W       = 80f;    // Status

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("confid",       "HEC_CONFID value (e.g. '000000000086')")
                .requiredString("conf_version", "HEC_CONF_VERSION value (e.g. '000001')")
                .requiredString("cdd_version_1","First CDD_VERSION to compare (e.g. '006')")
                .requiredString("cdd_version_2","Second CDD_VERSION to compare (e.g. '005')")
                .optionalString("session_id",   "Optional session ID from CreateSession")
                .optionalString("system_id",    "Optional SAP system ID")
                .buildTool("CompareCddHeader",
                        "Compare two CDD header records from /HEC1/R_HEADER_CDD. " +
                        "Provide HEC_CONFID, HEC_CONF_VERSION and two CDD_VERSION values. " +
                        "Fetches both records, compares all fields side by side, and generates " +
                        "a PDF report with differences highlighted. Returns the PDF file path.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        String confid      = requireString(args, "confid").trim();
        String confVersion = requireString(args, "conf_version").trim();
        String cddVer1     = requireString(args, "cdd_version_1").trim();
        String cddVer2     = requireString(args, "cdd_version_2").trim();

        log.info("CompareCddHeader: confid={} confVersion={} cddVer1={} cddVer2={}",
                confid, confVersion, cddVer1, cddVer2);

        return executeWithErrorHandling(args, "CompareCddHeader", (sessionId, resolved) -> {

            // ── 1. Fetch CDS data ─────────────────────────────────────────
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("ddlSourceName", CDS_VIEW);
            queryParams.put("rowNumber", String.valueOf(FETCH_ROWS));

            String xmlResponse = jcoSessionManager.executeInContext(sessionId, (dest, session) ->
                    adtClient.statelessPostViaRfc(dest, session,
                            "/sap/bc/adt/datapreview/cds", queryParams, "",
                            "application/xml",
                            "application/vnd.sap.adt.datapreview.table.v1+xml").getBody()
            );

            // ── 2. Parse columnar XML → Map<columnName, List<String>> ─────
            Map<String, List<String>> columns = parseColumnarXml(xmlResponse);
            if (columns.isEmpty()) {
                return error("No data returned from " + CDS_VIEW);
            }

            // ── 3. Find the two rows by key values ────────────────────────
            int rowIdx1 = findRowIndex(columns, confid, confVersion, cddVer1);
            int rowIdx2 = findRowIndex(columns, confid, confVersion, cddVer2);

            if (rowIdx1 < 0) {
                return error("Record not found: HEC_CONFID=" + confid
                        + " HEC_CONF_VERSION=" + confVersion + " CDD_VERSION=" + cddVer1);
            }
            if (rowIdx2 < 0) {
                return error("Record not found: HEC_CONFID=" + confid
                        + " HEC_CONF_VERSION=" + confVersion + " CDD_VERSION=" + cddVer2);
            }

            // ── 4. Build comparison rows ──────────────────────────────────
            List<String[]> rows = new ArrayList<>();   // [displayName, val1, val2, status]
            int diffCount = 0;
            for (String[] field : COMPARE_FIELDS) {
                String displayName = field[0];
                String colKey      = field[1];
                String v1 = getCell(columns, colKey, rowIdx1);
                String v2 = getCell(columns, colKey, rowIdx2);
                boolean differs = !v1.equals(v2);
                if (differs) diffCount++;
                rows.add(new String[]{displayName, v1, v2, differs ? "DIFFERENT" : "SAME"});
            }

            // ── 5. Generate PDF ───────────────────────────────────────────
            String title     = "CDD Header Comparison";
            String subtitle  = confid + " / " + confVersion + ":  CDD " + cddVer1 + "  vs  CDD " + cddVer2;
            String summary   = String.format("Total fields: %d  |  Differences: %d  |  Identical: %d",
                    COMPARE_FIELDS.size(), diffCount, COMPARE_FIELDS.size() - diffCount);
            String filename  = "cdd_compare_" + confid + "_" + confVersion
                    + "_" + cddVer1 + "_vs_" + cddVer2 + "_" + System.currentTimeMillis();

            byte[] pdfBytes = buildPdf(title, subtitle, summary, cddVer1, cddVer2, rows);

            String systemFileId = getSystemFileId(resolved);
            Path pdfPath = fileStorageService.writeBinary(
                    "documents", FileStorageService.CAT_DOCUMENTS, filename, pdfBytes, FileStorageService.EXT_PDF);

            log.info("CompareCddHeader PDF written: {} ({} bytes, {} diffs)", pdfPath, pdfBytes.length, diffCount);

            String systemHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient());

            return McpResponseFormatter.success(systemHeader, String.format(
                    "CDD Header Comparison Complete\n" +
                    "Keys : %s / %s\n" +
                    "Record 1: CDD_VERSION = %s\n" +
                    "Record 2: CDD_VERSION = %s\n" +
                    "%s\n" +
                    "PDF : %s",
                    confid, confVersion, cddVer1, cddVer2, summary, pdfPath));
        });
    }

    // ── XML parsing ─────────────────────────────────────────────────────────

    private Map<String, List<String>> parseColumnarXml(String xml) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            String NS = "http://www.sap.com/adt/dataPreview";
            NodeList colNodes = doc.getElementsByTagNameNS(NS, "columns");
            for (int i = 0; i < colNodes.getLength(); i++) {
                Element col = (Element) colNodes.item(i);

                // Get column name from metadata
                NodeList metaList = col.getElementsByTagNameNS(NS, "metadata");
                if (metaList.getLength() == 0) continue;
                Element meta = (Element) metaList.item(0);
                String colName = meta.getAttributeNS(NS, "name");
                if (colName == null || colName.isEmpty()) {
                    colName = meta.getAttribute("dataPreview:name");
                }
                if (colName == null || colName.isEmpty()) continue;

                // Get all data values
                List<String> values = new ArrayList<>();
                NodeList dataNodes = col.getElementsByTagNameNS(NS, "data");
                for (int j = 0; j < dataNodes.getLength(); j++) {
                    values.add(dataNodes.item(j).getTextContent().trim());
                }
                result.put(colName, values);
            }
        } catch (Exception e) {
            log.error("Failed to parse CDS XML", e);
        }
        return result;
    }

    private int findRowIndex(Map<String, List<String>> columns,
                              String confid, String confVersion, String cddVersion) {
        List<String> confIds  = columns.getOrDefault(COL_CONFID,  Collections.emptyList());
        List<String> confVers = columns.getOrDefault(COL_CONFVER, Collections.emptyList());
        List<String> cddVers  = columns.getOrDefault(COL_CDDVER,  Collections.emptyList());

        for (int i = 0; i < confIds.size(); i++) {
            if (confIds.get(i).equals(confid)
                    && i < confVers.size() && confVers.get(i).equals(confVersion)
                    && i < cddVers.size()  && cddVers.get(i).equals(cddVersion)) {
                return i;
            }
        }
        return -1;
    }

    private String getCell(Map<String, List<String>> columns, String colKey, int rowIdx) {
        List<String> vals = columns.getOrDefault(colKey, Collections.emptyList());
        if (rowIdx < 0 || rowIdx >= vals.size()) return "";
        String v = vals.get(rowIdx);
        return v == null ? "" : v;
    }

    // ── PDF generation ───────────────────────────────────────────────────────

    private byte[] buildPdf(String title, String subtitle, String summary,
                             String ver1, String ver2,
                             List<String[]> rows) throws Exception {

        try (PDDocument doc = new PDDocument()) {
            PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regFont  = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            float pageW = PDRectangle.A4.getWidth();
            float pageH = PDRectangle.A4.getHeight();
            float tableW = COL1_W + COL2_W + COL3_W + COL4_W;

            // ── Page 1 ────────────────────────────────────────────────────
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            float y = pageH - MARGIN;

            // Title
            y = writeText(cs, boldFont, TITLE_SIZE, MARGIN, y, title);
            y -= 4;
            y = writeText(cs, regFont, HEAD_SIZE, MARGIN, y, subtitle);
            y -= 4;
            y = writeText(cs, regFont, HEAD_SIZE, MARGIN, y, summary);
            y -= 10;

            // Table header
            y = drawTableHeader(cs, boldFont, HEAD_SIZE, MARGIN, y, ver1, ver2);
            y -= 2;

            // Table rows
            for (String[] row : rows) {
                if (y < MARGIN + ROW_H) {
                    // Start a new page
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = pageH - MARGIN;
                    y = drawTableHeader(cs, boldFont, HEAD_SIZE, MARGIN, y, ver1, ver2);
                    y -= 2;
                }
                boolean isDiff = "DIFFERENT".equals(row[3]);
                y = drawTableRow(cs, isDiff ? boldFont : regFont, BODY_SIZE, MARGIN, y, row, isDiff);
            }

            cs.close();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            return bos.toByteArray();
        }
    }

    private float writeText(PDPageContentStream cs, PDType1Font font, float size,
                              float x, float y, String text) throws Exception {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(safe(text, 120));
        cs.endText();
        return y - size - 4;
    }

    private float drawTableHeader(PDPageContentStream cs, PDType1Font font, float size,
                                   float x, float y, String ver1, String ver2) throws Exception {
        float rowTop = y;
        float rowBot = y - ROW_H;

        // Background line
        cs.setNonStrokingColor(0.85f, 0.85f, 0.85f);
        cs.addRect(x, rowBot, COL1_W + COL2_W + COL3_W + COL4_W, ROW_H);
        cs.fill();
        cs.setNonStrokingColor(0f, 0f, 0f);

        float textY = rowBot + 3;
        writeCell(cs, font, size, x,                            textY, "Field",                COL1_W);
        writeCell(cs, font, size, x + COL1_W,                  textY, "CDD " + ver1,           COL2_W);
        writeCell(cs, font, size, x + COL1_W + COL2_W,         textY, "CDD " + ver2,           COL3_W);
        writeCell(cs, font, size, x + COL1_W + COL2_W + COL3_W, textY, "Status",              COL4_W);

        drawRowBorder(cs, x, rowBot, COL1_W + COL2_W + COL3_W + COL4_W, ROW_H);
        return rowBot;
    }

    private float drawTableRow(PDPageContentStream cs, PDType1Font font, float size,
                                float x, float y, String[] row, boolean highlight) throws Exception {
        float rowBot = y - ROW_H;

        if (highlight) {
            cs.setNonStrokingColor(1f, 0.95f, 0.8f);
            cs.addRect(x, rowBot, COL1_W + COL2_W + COL3_W + COL4_W, ROW_H);
            cs.fill();
            cs.setNonStrokingColor(0f, 0f, 0f);
        }

        float textY = rowBot + 3;
        writeCell(cs, font, size, x,                             textY, row[0], COL1_W);
        writeCell(cs, font, size, x + COL1_W,                   textY, row[1], COL2_W);
        writeCell(cs, font, size, x + COL1_W + COL2_W,          textY, row[2], COL3_W);

        if (highlight) {
            cs.setNonStrokingColor(0.8f, 0f, 0f);
        }
        writeCell(cs, font, size, x + COL1_W + COL2_W + COL3_W, textY, row[3], COL4_W);
        cs.setNonStrokingColor(0f, 0f, 0f);

        drawRowBorder(cs, x, rowBot, COL1_W + COL2_W + COL3_W + COL4_W, ROW_H);
        return rowBot;
    }

    private void writeCell(PDPageContentStream cs, PDType1Font font, float size,
                            float x, float y, String text, float maxWidth) throws Exception {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x + 2, y);
        cs.showText(safe(text, (int)(maxWidth / (size * 0.55f))));
        cs.endText();
    }

    private void drawRowBorder(PDPageContentStream cs, float x, float y, float w, float h) throws Exception {
        cs.setStrokingColor(0.7f, 0.7f, 0.7f);
        cs.addRect(x, y, w, h);
        cs.stroke();
        cs.setStrokingColor(0f, 0f, 0f);
    }

    private String safe(String s, int maxLen) {
        if (s == null) return "";
        s = s.replace("\n", " ").replace("\r", "");
        return s.length() > maxLen ? s.substring(0, maxLen - 1) + "…" : s;
    }
}
