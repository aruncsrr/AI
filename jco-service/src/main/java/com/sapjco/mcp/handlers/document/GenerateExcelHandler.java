package com.sapjco.mcp.handlers.document;

import com.sapjco.mcp.handlers.AbstractWikiHandler;
import com.sapjco.mcp.mcp.ToolSchemaBuilder;
import com.sapjco.mcp.service.FileStorageService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Map;

/**
 * MCP handler for generate_excel — generate an Excel (.xlsx) workbook.
 */
@Slf4j
@Component
public class GenerateExcelHandler extends AbstractWikiHandler {

    @Override
    public Tool getToolDefinition() {
        return ToolSchemaBuilder.builder()
                .requiredString("title", "Workbook/sheet title (shown in row 1)")
                .requiredString("headers", "Comma-separated column headers (e.g. \"Name,Status,Owner,Date\")")
                .requiredString("rows", "JSON array of rows, each row is a comma-separated string: " +
                        "[\"val1,val2,val3\",\"val4,val5,val6\"]")
                .optionalString("sheet_name", "Worksheet name (default: Sheet1)")
                .optionalString("filename", "Output filename without extension (default: auto-generated)")
                .buildTool("generate_excel",
                        "Generate a Microsoft Excel (.xlsx) workbook with tabular data. " +
                        "Creates a formatted sheet with a title row, bold header row, and data rows. " +
                        "Output saved to /tmp/sap-mcp/documents/ and file path is returned.");
    }

    @Override
    public CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest request) {
        Map<String, Object> args = request.arguments();

        for (String required : new String[]{"title", "headers", "rows"}) {
            CallToolResult v = validateRequired(args, required);
            if (v != null) return v;
        }

        String title = requireString(args, "title");
        String headersRaw = requireString(args, "headers");
        String rowsJson = requireString(args, "rows");
        String sheetName = optionalString(args, "sheet_name", "Sheet1");
        String filename = optionalString(args, "filename",
                "spreadsheet_" + System.currentTimeMillis());

        log.info("generate_excel: title={} sheet={} filename={}", title, sheetName, filename);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet(sheetName);

            // Cell styles
            XSSFCellStyle titleStyle = createTitleStyle(wb);
            XSSFCellStyle headerStyle = createHeaderStyle(wb);
            XSSFCellStyle dataStyle = createDataStyle(wb);

            String[] headers = headersRaw.split(",");
            int numCols = headers.length;

            // Row 0: merged title
            XSSFRow titleRow = sheet.createRow(0);
            titleRow.setHeightInPoints(28);
            XSSFCell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(title);
            titleCell.setCellStyle(titleStyle);
            if (numCols > 1) {
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, numCols - 1));
            }

            // Row 1: headers
            XSSFRow headerRow = sheet.createRow(1);
            headerRow.setHeightInPoints(20);
            for (int c = 0; c < headers.length; c++) {
                XSSFCell cell = headerRow.createCell(c);
                cell.setCellValue(headers[c].trim());
                cell.setCellStyle(headerStyle);
            }

            // Data rows from JSON array
            int rowIdx = 2;
            String stripped = rowsJson.trim();
            if (stripped.startsWith("[")) stripped = stripped.substring(1);
            if (stripped.endsWith("]")) stripped = stripped.substring(0, stripped.length() - 1);

            for (String rowEntry : stripped.split("\",\\s*\"")) {
                String rowStr = rowEntry.replace("\"", "").trim();
                if (rowStr.isBlank()) continue;
                String[] cells = rowStr.split(",");
                XSSFRow dataRow = sheet.createRow(rowIdx++);
                for (int c = 0; c < cells.length; c++) {
                    XSSFCell cell = dataRow.createCell(c);
                    cell.setCellValue(cells[c].trim());
                    cell.setCellStyle(dataStyle);
                }
            }

            // Auto-size columns
            for (int c = 0; c < numCols; c++) {
                sheet.autoSizeColumn(c);
                // Cap at 60 chars width
                int width = Math.min(sheet.getColumnWidth(c), 15000);
                sheet.setColumnWidth(c, Math.max(width, 3000));
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            byte[] xlsxBytes = bos.toByteArray();

            Path filePath = fileStorageService.writeBinary("documents", FileStorageService.CAT_DOCUMENTS,
                    filename, xlsxBytes, FileStorageService.EXT_XLSX);

            return fileResponse(filePath, xlsxBytes.length,
                    "Excel Workbook Generated: " + title,
                    "Sheet: " + sheetName,
                    "Rows: " + (rowIdx - 2) + " data rows");
        } catch (Exception e) {
            log.error("generate_excel failed: {}", title, e);
            return error(e);
        }
    }

    private XSSFCellStyle createTitleStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[]{0, (byte) 51, (byte) 102}, null)); // SAP dark blue
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private XSSFCellStyle createHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[]{0, (byte) 112, (byte) 192}, null)); // SAP blue
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private XSSFCellStyle createDataStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setBorderBottom(BorderStyle.HAIR);
        style.setBorderRight(BorderStyle.HAIR);
        return style;
    }
}
