package com.sapjco.mcp.handlers.data;

import com.sap.conn.jco.JCoDestination;
import com.sap.conn.jco.JCoFunction;
import com.sap.conn.jco.JCoParameterList;
import com.sap.conn.jco.JCoTable;
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

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.*;

/**
 * MCP handler for SimulateNcdd.
 * Runs ABAP report /HEC1/NCDD_AUTOMATION_TEST as a background job via RFC,
 * reads the spool output, and generates a PDF report.
 *
 * Usage: "Simulate 000000012860 Version 1"
 *  → p_conf_i = 000000012860, p_conf_v = 1
 */
@Slf4j
@Component
public class SimulateNcddHandler extends AbstractReadHandler {

    private static final String REPORT_NAME  = "/HEC1/NCDD_AUTOMATION_TEST";
    private static final String JOB_NAME     = "MCP_NCDD_SIM";
    private static final int    MAX_WAIT_SEC = 120;
    private static final int    POLL_MS      = 3000;

    // ── PDF layout ─────────────────────────────────────────────────────────────
    private static final float MARGIN     = 40f;
    private static final float TITLE_SIZE = 14f;
    private static final float BODY_SIZE  = 8.5f;
    private static final float ROW_H      = 13f;
    private static final float COL_SID_W  = 80f;
    private static final float COL_DAT_W  = 70f;   // per date column
    private static final float COL_ST_W   = 60f;

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("conf_i",      "p_conf_i: Configuration ID (e.g. '000000012860')")
                .requiredString("conf_v",      "p_conf_v: Configuration version (e.g. '1' or '000001')")
                .optionalString("session_id",  "Optional session ID from CreateSession")
                .optionalString("system_id",   "Optional SAP system ID")
                .buildTool("SimulateNcdd",
                        "Run /HEC1/NCDD_AUTOMATION_TEST simulation for a configuration. " +
                        "Provide conf_i (p_conf_i) and conf_v (p_conf_v). " +
                        "Executes as a background job, reads spool output, and generates a PDF report. " +
                        "Returns the PDF file path.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        String confI = requireString(args, "conf_i").trim();
        String confV = requireString(args, "conf_v").trim();

        log.info("SimulateNcdd: conf_i={} conf_v={}", confI, confV);

        return executeWithErrorHandling(args, "SimulateNcdd", (sessionId, resolved) -> {

            // ── 1. Submit background job ───────────────────────────────────
            long[] jobInfo = submitJob(sessionId, confI, confV);
            long jobCount  = jobInfo[0];
            String jobNameUsed = JOB_NAME;

            log.info("SimulateNcdd: job submitted jobCount={}", jobCount);

            // ── 2. Poll for completion ─────────────────────────────────────
            waitForJobCompletion(sessionId, jobNameUsed, jobCount);

            // ── 3. Read spool list ─────────────────────────────────────────
            List<String> spoolLines = readSpool(sessionId, jobNameUsed, jobCount);
            log.info("SimulateNcdd: spool lines={}", spoolLines.size());

            // ── 4. Parse spool into result rows ────────────────────────────
            List<SpoolRow> rows = parseSpoolLines(spoolLines);

            // ── 5. Build PDF ───────────────────────────────────────────────
            String title    = "NCDD Simulation: " + confI + " v" + confV;
            String filename = "ncdd_sim_" + confI + "_v" + confV + "_" + System.currentTimeMillis();
            byte[] pdfBytes = buildPdf(title, confI, confV, spoolLines, rows);

            Path pdfPath = fileStorageService.writeBinary(
                    "documents", FileStorageService.CAT_DOCUMENTS, filename, pdfBytes, FileStorageService.EXT_PDF);

            log.info("SimulateNcdd PDF written: {} ({} bytes)", pdfPath, pdfBytes.length);

            String sysHeader = McpResponseFormatter.formatSystemHeader(
                    resolved.getSystemId(),
                    resolved.getConfig().getEffectiveHost(),
                    resolved.getConfig().getClient());

            return McpResponseFormatter.success(sysHeader, String.format(
                    "NCDD Simulation Complete\n" +
                    "Configuration : %s / %s\n" +
                    "Spool lines   : %d\n" +
                    "PDF           : %s",
                    confI, confV, spoolLines.size(), pdfPath));
        });
    }

    // ── Background job submission ───────────────────────────────────────────────

    /**
     * Open job, submit report with selection parameters, close job.
     * Returns [jobCount, 0] where jobCount is the SAP job number.
     */
    private long[] submitJob(String sessionId, String confI, String confV) throws Exception {
        return jcoSessionManager.executeInContext(sessionId, (dest, session) -> {
            // Open the job
            long jobCount = openJob(dest, JOB_NAME);

            // Submit the report as a step inside the job
            submitReportStep(dest, JOB_NAME, jobCount, confI, confV);

            // Close (release) the job — SAP will schedule it immediately
            closeJob(dest, JOB_NAME, jobCount);

            return new long[]{jobCount, 0};
        });
    }

    private long openJob(JCoDestination dest, String jobName) throws Exception {
        JCoFunction fn = dest.getRepository().getFunction("JOB_OPEN");
        if (fn == null) throw new RuntimeException("RFC JOB_OPEN not found");
        fn.getImportParameterList().setValue("JOBNAME", jobName);
        fn.execute(dest);
        return fn.getExportParameterList().getLong("JOBCOUNT");
    }

    private void submitReportStep(JCoDestination dest, String jobName, long jobCount,
                                   String confI, String confV) throws Exception {
        JCoFunction fn = dest.getRepository().getFunction("JOB_SUBMIT");
        if (fn == null) throw new RuntimeException("RFC JOB_SUBMIT not found");

        JCoParameterList imp = fn.getImportParameterList();
        imp.setValue("JOBNAME",   jobName);
        imp.setValue("JOBCOUNT",  jobCount);
        imp.setValue("REPORT",    REPORT_NAME);
        imp.setValue("LANGUAGE",  "EN");
        // Build selection-screen parameter table (PARAMS)
        JCoTable params = fn.getTableParameterList().getTable("PARAMS");
        addParam(params, "P_CONF_I", confI);
        addParam(params, "P_CONF_V", confV);
        // Leave checkboxes at defaults (space = off, but they default to 'X' in report INITIALIZATION)

        fn.execute(dest);
    }

    private void addParam(JCoTable params, String selName, String low) {
        params.appendRow();
        params.setValue("SELNAME", selName);
        params.setValue("KIND",    "P");   // P=parameter, S=select-option
        params.setValue("SIGN",    "I");
        params.setValue("OPTION",  "EQ");
        params.setValue("LOW",     low);
    }

    private void closeJob(JCoDestination dest, String jobName, long jobCount) throws Exception {
        JCoFunction fn = dest.getRepository().getFunction("JOB_CLOSE");
        if (fn == null) throw new RuntimeException("RFC JOB_CLOSE not found");
        JCoParameterList imp = fn.getImportParameterList();
        imp.setValue("JOBNAME",       jobName);
        imp.setValue("JOBCOUNT",      jobCount);
        imp.setValue("STRTIMMED",     "X");   // Start immediately
        fn.execute(dest);
    }

    // ── Job completion polling ─────────────────────────────────────────────────

    private void waitForJobCompletion(String sessionId, String jobName, long jobCount) throws Exception {
        long deadline = System.currentTimeMillis() + (long) MAX_WAIT_SEC * 1000;
        while (System.currentTimeMillis() < deadline) {
            String status = getJobStatus(sessionId, jobName, jobCount);
            log.debug("SimulateNcdd: job status={}", status);
            if ("F".equals(status) || "A".equals(status)) {
                // F=finished, A=aborted
                if ("A".equals(status)) {
                    throw new RuntimeException("Background job " + jobName + "/" + jobCount + " aborted");
                }
                return;
            }
            Thread.sleep(POLL_MS);
        }
        throw new RuntimeException("Background job did not finish within " + MAX_WAIT_SEC + " seconds");
    }

    private String getJobStatus(String sessionId, String jobName, long jobCount) throws Exception {
        return jcoSessionManager.executeInContext(sessionId, (dest, session) -> {
            JCoFunction fn = dest.getRepository().getFunction("BP_JOB_STATUS_GET");
            if (fn == null) throw new RuntimeException("RFC BP_JOB_STATUS_GET not found");
            fn.getImportParameterList().setValue("JOBNAME",  jobName);
            fn.getImportParameterList().setValue("JOBCOUNT", String.valueOf(jobCount));
            fn.execute(dest);
            return fn.getExportParameterList().getString("STATUS");
        });
    }

    // ── Spool reading ──────────────────────────────────────────────────────────

    private List<String> readSpool(String sessionId, String jobName, long jobCount) throws Exception {
        return jcoSessionManager.executeInContext(sessionId, (dest, session) -> {
            // First get the spool ID via BP_JOB_READ
            JCoFunction jobRead = dest.getRepository().getFunction("BP_JOB_READ");
            if (jobRead == null) throw new RuntimeException("RFC BP_JOB_READ not found");
            jobRead.getImportParameterList().setValue("JOBNAME",  jobName);
            jobRead.getImportParameterList().setValue("JOBCOUNT", String.valueOf(jobCount));
            jobRead.execute(dest);

            JCoTable stepList = jobRead.getTableParameterList().getTable("STEP_LIST");
            long spoolId = 0;
            if (stepList != null && stepList.getNumRows() > 0) {
                stepList.setRow(0);
                spoolId = stepList.getLong("LISTIDENT");
            }

            if (spoolId == 0) {
                log.warn("SimulateNcdd: no spool ID found for job {}/{}", jobName, jobCount);
                return Collections.emptyList();
            }

            log.info("SimulateNcdd: reading spool ID {}", spoolId);
            return readSpoolById(dest, spoolId);
        });
    }

    private List<String> readSpoolById(JCoDestination dest, long spoolId) throws Exception {
        JCoFunction fn = dest.getRepository().getFunction("RSPO_R_RLIST_GET");
        if (fn == null) {
            // Fallback: try RSSPO_R_LIST_GET
            fn = dest.getRepository().getFunction("RSSPO_R_LIST_GET");
        }
        if (fn == null) throw new RuntimeException("No spool reading RFC found (RSPO_R_RLIST_GET)");

        fn.getImportParameterList().setValue("RQIDENT", spoolId);
        fn.execute(dest);

        JCoTable lines = fn.getTableParameterList().getTable("LINE");
        List<String> result = new ArrayList<>();
        if (lines != null) {
            for (int i = 0; i < lines.getNumRows(); i++) {
                lines.setRow(i);
                result.add(lines.getString("LINE"));
            }
        }
        return result;
    }

    // ── Spool parsing ──────────────────────────────────────────────────────────

    /** Represents one parsed result row from the spool output. */
    private static class SpoolRow {
        String sid;           // tier SID or level_id
        String startDate;
        String endDate;
        String buildStart;
        String finalDate;
        String status;        // GREEN / YELLOW / RED
        String extra;         // remaining line text

        SpoolRow(String sid, String startDate, String endDate,
                 String buildStart, String finalDate, String status, String extra) {
            this.sid        = sid;
            this.startDate  = startDate;
            this.endDate    = endDate;
            this.buildStart = buildStart;
            this.finalDate  = finalDate;
            this.status     = status;
            this.extra      = extra;
        }
    }

    /**
     * Very light heuristic parser.
     * The spool lines from the ABAP LIST output are plain text.
     * We look for lines starting with a SID/level token followed by dates.
     */
    private List<SpoolRow> parseSpoolLines(List<String> lines) {
        List<SpoolRow> rows = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // Date pattern: YYYYMMDD (8 digits)
            // Lines with at least two dates are likely result rows
            long dateCount = countDateTokens(trimmed);
            if (dateCount >= 2) {
                String[] parts = trimmed.split("\\s+");
                String sid       = parts.length > 0 ? parts[0] : "";
                String startDate = extractDate(trimmed, 0);
                String endDate   = extractDate(trimmed, 1);
                String buildStart= extractDate(trimmed, 2);
                String finalDate = extractDate(trimmed, 3);
                String status    = deriveStatus(trimmed);
                rows.add(new SpoolRow(sid, startDate, endDate, buildStart, finalDate, status, trimmed));
            }
        }
        return rows;
    }

    private long countDateTokens(String line) {
        return Arrays.stream(line.split("\\s+"))
                .filter(t -> t.matches("\\d{8}"))
                .count();
    }

    private String extractDate(String line, int index) {
        List<String> dates = new ArrayList<>();
        for (String t : line.split("\\s+")) {
            if (t.matches("\\d{8}")) dates.add(formatDate(t));
        }
        return index < dates.size() ? dates.get(index) : "";
    }

    private String formatDate(String d) {
        if (d == null || d.length() != 8) return d;
        return d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8);
    }

    /**
     * In the ABAP report, color is set by FORMAT COLOR:
     *   COL_TOTAL (yellow)    → xreference = X
     *   COL_POSITIVE (green)  → build_start_date not empty
     *   COL_NEGATIVE (red)    → build_start_date empty
     * We cannot detect color from plain spool text, so we infer from build start date presence.
     */
    private String deriveStatus(String line) {
        String buildDate = extractDate(line, 2);
        if (!buildDate.isEmpty()) return "GREEN";
        return "RED";
    }

    // ── PDF generation ─────────────────────────────────────────────────────────

    private byte[] buildPdf(String title, String confI, String confV,
                             List<String> rawLines, List<SpoolRow> rows) throws Exception {

        try (PDDocument doc = new PDDocument()) {
            PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regFont  = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font monoFont = new PDType1Font(Standard14Fonts.FontName.COURIER);

            float pageH = PDRectangle.A4.getHeight();
            float pageW = PDRectangle.A4.getWidth();

            // ── Page 1: summary table ────────────────────────────────────────
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            float y = pageH - MARGIN;

            y = writeText(cs, boldFont, TITLE_SIZE, MARGIN, y, title);
            y -= 4;
            y = writeText(cs, regFont, 9f, MARGIN, y,
                    "Configuration: " + confI + "   Version: " + confV +
                    "   Result rows: " + rows.size());
            y -= 10;

            if (!rows.isEmpty()) {
                // Header
                y = drawSummaryHeader(cs, boldFont, MARGIN, y);

                for (SpoolRow row : rows) {
                    if (y < MARGIN + ROW_H) {
                        cs.close();
                        page = new PDPage(PDRectangle.A4);
                        doc.addPage(page);
                        cs = new PDPageContentStream(doc, page);
                        y = pageH - MARGIN;
                        y = drawSummaryHeader(cs, boldFont, MARGIN, y);
                    }
                    y = drawSummaryRow(cs, boldFont, regFont, MARGIN, y, row);
                }
            } else {
                y = writeText(cs, regFont, BODY_SIZE, MARGIN, y,
                        "No structured result rows detected — see raw spool output below.");
                y -= 6;
            }

            cs.close();

            // ── Page 2+: raw spool output ────────────────────────────────────
            if (!rawLines.isEmpty()) {
                page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                cs = new PDPageContentStream(doc, page);
                y = pageH - MARGIN;

                y = writeText(cs, boldFont, 11f, MARGIN, y, "Raw Spool Output");
                y -= 6;

                for (String line : rawLines) {
                    if (y < MARGIN + 10f) {
                        cs.close();
                        page = new PDPage(PDRectangle.A4);
                        doc.addPage(page);
                        cs = new PDPageContentStream(doc, page);
                        y = pageH - MARGIN;
                    }
                    y = writeText(cs, monoFont, 7f, MARGIN, y, safeLine(line, 130));
                }
                cs.close();
            }

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
        cs.showText(safeLine(text, 200));
        cs.endText();
        return y - size - 3;
    }

    private float drawSummaryHeader(PDPageContentStream cs, PDType1Font font, float x, float y) throws Exception {
        float rowBot = y - ROW_H;
        cs.setNonStrokingColor(0.8f, 0.8f, 0.8f);
        cs.addRect(x, rowBot, colsTotal(), ROW_H);
        cs.fill();
        cs.setNonStrokingColor(0f, 0f, 0f);

        float tx = rowBot + 3;
        writeCell(cs, font, BODY_SIZE, x,                                tx, "SID/Level",    COL_SID_W);
        writeCell(cs, font, BODY_SIZE, x + COL_SID_W,                   tx, "Phase Start",  COL_DAT_W);
        writeCell(cs, font, BODY_SIZE, x + COL_SID_W + COL_DAT_W,       tx, "Phase End",    COL_DAT_W);
        writeCell(cs, font, BODY_SIZE, x + COL_SID_W + COL_DAT_W * 2,   tx, "Build Start",  COL_DAT_W);
        writeCell(cs, font, BODY_SIZE, x + COL_SID_W + COL_DAT_W * 3,   tx, "Final CDD",    COL_DAT_W);
        writeCell(cs, font, BODY_SIZE, x + COL_SID_W + COL_DAT_W * 4,   tx, "Status",       COL_ST_W);

        drawBorder(cs, x, rowBot, colsTotal(), ROW_H);
        return rowBot;
    }

    private float drawSummaryRow(PDPageContentStream cs, PDType1Font bold,
                                  PDType1Font reg, float x, float y, SpoolRow row) throws Exception {
        float rowBot = y - ROW_H;

        boolean isGreen  = "GREEN".equals(row.status);
        boolean isRed    = "RED".equals(row.status);
        boolean isYellow = "YELLOW".equals(row.status);

        if (isGreen) {
            cs.setNonStrokingColor(0.9f, 1f, 0.9f);
        } else if (isRed) {
            cs.setNonStrokingColor(1f, 0.9f, 0.9f);
        } else if (isYellow) {
            cs.setNonStrokingColor(1f, 1f, 0.8f);
        }
        if (isGreen || isRed || isYellow) {
            cs.addRect(x, rowBot, colsTotal(), ROW_H);
            cs.fill();
            cs.setNonStrokingColor(0f, 0f, 0f);
        }

        float tx = rowBot + 3;
        PDType1Font font = reg;
        writeCell(cs, font, BODY_SIZE, x,                              tx, row.sid,        COL_SID_W);
        writeCell(cs, font, BODY_SIZE, x + COL_SID_W,                 tx, row.startDate,  COL_DAT_W);
        writeCell(cs, font, BODY_SIZE, x + COL_SID_W + COL_DAT_W,     tx, row.endDate,    COL_DAT_W);
        writeCell(cs, font, BODY_SIZE, x + COL_SID_W + COL_DAT_W * 2, tx, row.buildStart, COL_DAT_W);
        writeCell(cs, font, BODY_SIZE, x + COL_SID_W + COL_DAT_W * 3, tx, row.finalDate,  COL_DAT_W);

        if (isRed) cs.setNonStrokingColor(0.8f, 0f, 0f);
        writeCell(cs, font, BODY_SIZE, x + COL_SID_W + COL_DAT_W * 4, tx, row.status, COL_ST_W);
        cs.setNonStrokingColor(0f, 0f, 0f);

        drawBorder(cs, x, rowBot, colsTotal(), ROW_H);
        return rowBot;
    }

    private float colsTotal() {
        return COL_SID_W + COL_DAT_W * 4 + COL_ST_W;
    }

    private void writeCell(PDPageContentStream cs, PDType1Font font, float size,
                            float x, float y, String text, float maxW) throws Exception {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x + 2, y);
        cs.showText(safeLine(text, (int) (maxW / (size * 0.55f))));
        cs.endText();
    }

    private void drawBorder(PDPageContentStream cs, float x, float y, float w, float h) throws Exception {
        cs.setStrokingColor(0.7f, 0.7f, 0.7f);
        cs.addRect(x, y, w, h);
        cs.stroke();
        cs.setStrokingColor(0f, 0f, 0f);
    }

    private String safeLine(String s, int maxLen) {
        if (s == null) return "";
        s = s.replace("\n", " ").replace("\r", "").replace("\t", "  ");
        // Replace any non-Latin-1 characters with '?'
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(c <= 0xFF ? c : '?');
        }
        s = sb.toString();
        return s.length() > maxLen ? s.substring(0, maxLen - 1) + ">" : s;
    }
}
