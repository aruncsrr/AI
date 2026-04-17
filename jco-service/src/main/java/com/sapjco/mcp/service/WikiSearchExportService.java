package com.sapjco.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.presentationml.x2006.main.CTSlideIdList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates PPTX exports for wiki search results.
 * Loads ~/Desktop/CDD.pptx, keeps its original Cover and Thank-you slides,
 * injects content slides in between, then moves the Thank-you to the end.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiSearchExportService {

    @Autowired
    private FileStorageService fileStorageService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TEMPLATE_PATH =
            System.getProperty("user.home") + "/Desktop/CDD.pptx";

    // SAP colors for content overlays
    private static final Color NAVY     = Color.decode("#003366");
    private static final Color SAP_BLUE = Color.decode("#0070B8");
    private static final Color SAP_GRN  = Color.decode("#107E3E");
    private static final Color LT_BLUE  = Color.decode("#C8E0F4");
    private static final Color MUT_BLUE = Color.decode("#AACCEE");
    private static final Color DARK_TXT = Color.decode("#1A1A1A");
    private static final Color WHITE    = Color.WHITE;

    // Slide canvas: CDD template is 12192000 × 6858000 EMU = 960 × 540 pt
    private static final double SW = 960, SH = 540, M = 36;

    // Safe content zone on Blank layout slides (below master header / above footer)
    private static final double CT = 90, CB = 508;

    public record WikiResult(String title, String space, String url, String lastModified) {}
    public record ExportResult(Path pptxPath) {}

    // =========================================================================
    public ExportResult generate(String query, String jsonResponse) {
        List<WikiResult> results = parseResults(jsonResponse);
        String base = "wiki_search_" + fileStorageService.sanitizeFilename(query)
                      + "_" + System.currentTimeMillis();
        Path pptxPath = null;
        try { pptxPath = generatePptx(query, results, base); }
        catch (Exception e) { log.error("PPTX generation failed", e); }
        return new ExportResult(pptxPath);
    }

    // =========================================================================
    private List<WikiResult> parseResults(String json) {
        List<WikiResult> list = new ArrayList<>();
        try {
            JsonNode arr = MAPPER.readTree(json).path("results");
            if (!arr.isArray()) return list;
            for (JsonNode n : arr) {
                String title = n.path("title").asText("-");
                String space = n.path("space").path("name").asText(
                               n.path("space").path("key").asText("-"));
                String url   = n.path("_links").path("webui").asText("");
                String when  = n.path("version").path("when").asText("");
                if (when.length() > 10) when = when.substring(0, 10);
                list.add(new WikiResult(title, space, url, when));
            }
        } catch (Exception e) { log.warn("Failed to parse wiki results JSON", e); }
        return list;
    }

    // =========================================================================
    private Path generatePptx(String query, List<WikiResult> results, String filename)
            throws Exception {

        File tplFile = new File(TEMPLATE_PATH);

        if (!tplFile.exists()) {
            log.warn("Template not found at {}, using plain fallback", TEMPLATE_PATH);
            return generateFallbackPptx(query, results, filename);
        }

        log.info("Using company template: {}", TEMPLATE_PATH);
        XMLSlideShow pptx = new XMLSlideShow(new FileInputStream(tplFile));
        List<XSLFSlide> origSlides = new ArrayList<>(pptx.getSlides());
        // origSlides: [cover(0), content1(1), content2(2), thankYou(3)]

        // ── 1. Modify cover slide in-place ────────────────────────────────────
        XSLFSlide coverSlide = origSlides.get(0);
        updateTitle(coverSlide, "Title 1", "Wiki Search Results");
        // Add query label + stats badge over the left content area of Cover A
        text(coverSlide, trunc("\"" + query + "\"", 58), M, SH * 0.68, SW * 0.46, 26, 12.5, false, NAVY);
        rect(coverSlide, M, SH * 0.68 + 30, 170, 26, SAP_BLUE, null, 0);
        text(coverSlide, results.size() + " results found", M + 10, SH * 0.68 + 36, 158, 18, 10.0, true, WHITE);
        text(coverSlide, LocalDate.now().toString(), M, SH - 22, 200, 16, 8.5, false, Color.decode("#555555"));

        // ── 2. Modify thank-you slide in-place ───────────────────────────────
        XSLFSlide tySlide = origSlides.get(origSlides.size() - 1);
        text(tySlide, "Wiki Search: " + trunc(query, 65), M, SH * 0.64, SW * 0.50, 22, 10.5, false, Color.decode("#444444"));

        // ── 3. Remove original content slides (indices n-2 … 1, back-to-front) ─
        for (int i = origSlides.size() - 2; i >= 1; i--) {
            pptx.removeSlide(i);
        }
        // pptx now: [cover(0), thankYou(1)]

        // ── 4. Get Blank layout for content slides ────────────────────────────
        XSLFSlideLayout blankLayout = findLayout(pptx, "Blank", SlideLayout.BLANK);

        // ── 5. Append content slides (go to positions 2, 3, …) ───────────────
        addSummarySlide(pptx, blankLayout, query, results);
        for (int i = 0; i < results.size(); i += 3) {
            List<WikiResult> chunk = results.subList(i, Math.min(i + 3, results.size()));
            addResultsSlide(pptx, blankLayout, query, chunk,
                    (i / 3) + 1, (results.size() + 2) / 3);
        }
        // pptx now: [cover(0), thankYou(1), summary(2), results(3…)]

        // ── 6. Move thankYou from index 1 to the last position ───────────────
        int total = pptx.getSlides().size();
        moveSlide(pptx, 1, total - 1);
        // pptx now: [cover(0), summary(1), results(2…n-2), thankYou(n-1)]

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        pptx.write(bos);
        pptx.close();
        return fileStorageService.writeBinary(
                "wiki", FileStorageService.CAT_WIKI, filename,
                bos.toByteArray(), FileStorageService.EXT_PPTX);
    }

    /**
     * Move a slide from {@code from} index to {@code to} index using XMLBeans XmlCursor.
     * This reorders entries in CTPresentation.sldIdLst without touching slide content.
     */
    private void moveSlide(XMLSlideShow pptx, int from, int to) {
        if (from == to) return;
        CTSlideIdList sldIdLst = pptx.getCTPresentation().getSldIdLst();
        int size = sldIdLst.sizeOfSldIdArray();
        if (from < 0 || from >= size || to < 0 || to >= size) return;

        XmlObject slideToMove = sldIdLst.getSldIdArray(from);

        if (to >= size - 1) {
            try (XmlCursor src = slideToMove.newCursor();
                 XmlCursor end = sldIdLst.newCursor()) {
                end.toEndToken();
                src.moveXml(end);
            }
        } else {
            XmlObject target = sldIdLst.getSldIdArray(to > from ? to + 1 : to);
            try (XmlCursor src = slideToMove.newCursor();
                 XmlCursor tgt = target.newCursor()) {
                src.moveXml(tgt);
            }
        }
    }

    /** Update the text of a named placeholder on an existing slide. */
    private void updateTitle(XSLFSlide slide, String shapeName, String newText) {
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape ts
                    && shapeName.equals(ts.getShapeName())) {
                ts.clearText();
                XSLFTextParagraph p = ts.addNewTextParagraph();
                XSLFTextRun r = p.addNewTextRun();
                r.setText(newText);
                r.setFontSize(30.0);
                return;
            }
        }
    }

    // ── Summary slide ─────────────────────────────────────────────────────────
    private void addSummarySlide(XMLSlideShow pptx, XSLFSlideLayout layout,
                                  String query, List<WikiResult> results) {
        XSLFSlide slide = pptx.createSlide(layout);

        rect(slide, 0, CT, SW, 26, NAVY, null, 0);
        text(slide, "Search Summary", M, CT + 5, 360, 20, 12.5, true, WHITE);
        text(slide, trunc("\"" + query + "\"", 65), M + 250, CT + 5, 560, 20, 9.5, false, LT_BLUE);

        double cY = CT + 32, cH = CB - cY, cW = (SW - 3 * M) / 2;

        Set<String> spaces = new LinkedHashSet<>();
        results.forEach(r -> { if (r.space() != null && !"-".equals(r.space())) spaces.add(r.space()); });

        // Left card — stats
        double lx = M;
        rect(slide, lx, cY, cW, cH, WHITE, SAP_BLUE, 1.5f);
        rect(slide, lx, cY, cW, 24, SAP_BLUE, null, 0);
        text(slide, "KEY STATISTICS", lx + 8, cY + 5, cW - 16, 18, 9.5, true, WHITE);
        double ry = cY + 32;
        statRow(slide, lx + 8, ry,      cW - 16, "Total Results", String.valueOf(results.size()), SAP_BLUE);
        statRow(slide, lx + 8, ry + 30, cW - 16, "Unique Spaces", String.valueOf(spaces.size()),  SAP_BLUE);
        statRow(slide, lx + 8, ry + 60, cW - 16, "Search Date",   LocalDate.now().toString(),     SAP_BLUE);
        statRow(slide, lx + 8, ry + 90, cW - 16, "Query",         trunc(query, 38),               SAP_BLUE);

        // Right card — spaces
        double rx = M * 2 + cW;
        rect(slide, rx, cY, cW, cH, WHITE, SAP_GRN, 1.5f);
        rect(slide, rx, cY, cW, 24, SAP_GRN, null, 0);
        text(slide, "WIKI SPACES", rx + 8, cY + 5, cW - 16, 18, 9.5, true, WHITE);
        double sy = cY + 32;
        int maxSp = (int) ((cH - 40) / 21), sIdx = 0;
        for (String sp : spaces) {
            if (sIdx++ >= maxSp) break;
            rect(slide, rx + 8, sy, cW - 16, 17, Color.decode("#EEF3FA"), null, 0);
            rect(slide, rx + 8, sy, 4, 17, SAP_GRN, null, 0);
            text(slide, trunc(sp, 40), rx + 18, sy + 2, cW - 30, 14, 9.0, false, DARK_TXT);
            sy += 21;
        }
    }

    // ── Results slide ─────────────────────────────────────────────────────────
    private void addResultsSlide(XMLSlideShow pptx, XSLFSlideLayout layout,
                                  String query, List<WikiResult> chunk,
                                  int pageIdx, int totalPages) {
        XSLFSlide slide = pptx.createSlide(layout);

        rect(slide, 0, CT, SW, 26, NAVY, null, 0);
        text(slide, "Search Results", M, CT + 5, 360, 20, 12.5, true, WHITE);
        text(slide, trunc("\"" + query + "\"", 50) + "  ·  " + chunk.size() + " result(s)",
                M + 210, CT + 5, 520, 20, 9.5, false, LT_BLUE);
        text(slide, pageIdx + " / " + totalPages, SW - 56, CT + 5, 50, 18, 8.5, false, MUT_BLUE);

        double cH  = (CB - CT - 32 - (chunk.size() - 1) * 8.0) / Math.max(1, chunk.size());
        double cW  = SW - 2 * M;
        Color[] ac = { SAP_BLUE, SAP_GRN, Color.decode("#E76500") };

        for (int i = 0; i < chunk.size(); i++) {
            WikiResult r = chunk.get(i);
            Color acc    = ac[i % ac.length];
            double cy    = CT + 32 + i * (cH + 8);

            rect(slide, M, cy, cW, cH, WHITE, acc, 1.5f);
            rect(slide, M, cy, 5,  cH, acc,  null, 0);
            rect(slide, M + 5, cy, cW - 5, 23,
                    new Color(acc.getRed(), acc.getGreen(), acc.getBlue(), 22), null, 0);
            text(slide, trunc(r.title(), 85), M + 12, cy + 5, cW - 110, 17, 10.5, true, acc);

            double bW = Math.min(r.space().length() * 7.0 + 16, 155);
            rect(slide, M + cW - bW - 8, cy + 4, bW, 15, acc, null, 0);
            text(slide, trunc(r.space(), 20), M + cW - bW - 4, cy + 6, bW - 4, 12, 7.5, true, WHITE);

            text(slide, "URL:  " + trunc(r.url(), 88),
                    M + 12, cy + 29, cW - 20, 14, 8.0, false, DARK_TXT);
            text(slide, "Last modified:  " + r.lastModified(),
                    M + 12, cy + 43, 300, 13, 8.0, false, Color.decode("#555555"));
        }
    }

    // ── Plain fallback (no template file) ─────────────────────────────────────
    private Path generateFallbackPptx(String query, List<WikiResult> results, String filename)
            throws Exception {
        XMLSlideShow pptx = new XMLSlideShow();
        pptx.setPageSize(new java.awt.Dimension((int) SW, (int) SH));
        XSLFSlideLayout blank = pptx.getSlideMasters().get(0).getLayout(SlideLayout.BLANK);
        addSummarySlide(pptx, blank, query, results);
        for (int i = 0; i < results.size(); i += 3) {
            List<WikiResult> chunk = results.subList(i, Math.min(i + 3, results.size()));
            addResultsSlide(pptx, blank, query, chunk,
                    (i / 3) + 1, (results.size() + 2) / 3);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        pptx.write(bos);
        pptx.close();
        return fileStorageService.writeBinary(
                "wiki", FileStorageService.CAT_WIKI, filename,
                bos.toByteArray(), FileStorageService.EXT_PPTX);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private XSLFSlideLayout findLayout(XMLSlideShow pptx, String name, SlideLayout fallback) {
        for (XSLFSlideMaster master : pptx.getSlideMasters()) {
            for (XSLFSlideLayout layout : master.getSlideLayouts()) {
                if (name.equalsIgnoreCase(layout.getName())) return layout;
            }
        }
        try { return pptx.getSlideMasters().get(0).getLayout(fallback); }
        catch (Exception e) { return pptx.getSlideMasters().get(0).getSlideLayouts()[0]; }
    }

    private void statRow(XSLFSlide slide, double x, double y, double w,
                          String label, String value, Color accent) {
        rect(slide, x, y, w, 25, Color.decode("#F7F9FC"), Color.decode("#E0E8F0"), 0.5f);
        text(slide, label, x + 7, y + 6, w * 0.45, 17, 8.5, true, accent);
        text(slide, value, x + w * 0.46, y + 6, w * 0.52, 17, 8.5, false, DARK_TXT);
    }

    private XSLFAutoShape rect(XSLFSlide slide, double x, double y, double w, double h,
                                Color fill, Color border, float borderPt) {
        XSLFAutoShape s = slide.createAutoShape();
        s.setShapeType(ShapeType.RECT);
        s.setAnchor(new Rectangle2D.Double(x, y, w, h));
        s.setFillColor(fill);
        if (border != null && borderPt > 0) { s.setLineColor(border); s.setLineWidth(borderPt); }
        else { s.setLineWidth(0.0); }
        return s;
    }

    private XSLFTextBox text(XSLFSlide slide, String content,
                              double x, double y, double w, double h,
                              double fontSize, boolean bold, Color color) {
        XSLFTextBox tb = slide.createTextBox();
        tb.setAnchor(new Rectangle2D.Double(x, y, w, h));
        tb.setVerticalAlignment(VerticalAlignment.TOP);
        tb.clearText();
        XSLFTextParagraph para = tb.addNewTextParagraph();
        para.setTextAlign(TextParagraph.TextAlign.LEFT);
        XSLFTextRun run = para.addNewTextRun();
        run.setText(content);
        run.setFontSize(fontSize);
        run.setBold(bold);
        run.setFontColor(color);
        return tb;
    }

    private String trunc(String s, int max) {
        if (s == null) return "";
        s = s.replace("\n", " ").trim();
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
