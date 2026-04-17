package com.sapjco.mcp.web;

import com.sapjco.mcp.web.service.AnthropicChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Web UI controller — serves the chat frontend and handles API requests.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final AnthropicChatService chatService;

    /** Serve the main chat UI */
    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }

    /**
     * Chat endpoint — receives conversation history, calls Claude with all tools,
     * executes any tool calls, and returns the final assistant message.
     */
    @PostMapping(value = "/api/chat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
            if (messages == null || messages.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "messages array is required"));
            }

            Map<String, Object> result = chatService.chat(messages);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Chat request failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    /**
     * File download endpoint — serves generated files as attachment.
     * Only paths within the system temp directory are allowed.
     */
    @GetMapping("/api/download")
    public ResponseEntity<Resource> download(@RequestParam("path") String filePath) {
        return serveFile(filePath, true);
    }

    /**
     * File view endpoint — serves generated files inline (opens in browser).
     * Only paths within the system temp directory are allowed.
     */
    @GetMapping("/api/view")
    public ResponseEntity<Resource> view(@RequestParam("path") String filePath) {
        return serveFile(filePath, false);
    }

    private ResponseEntity<Resource> serveFile(String filePath, boolean asAttachment) {
        try {
            String tmpDir = System.getProperty("java.io.tmpdir");

            // Translate /tmp/... to actual tmpdir (handles macOS where /tmp is a symlink)
            String translatedPath = filePath;
            if (filePath.startsWith("/tmp/")) {
                translatedPath = tmpDir + (tmpDir.endsWith("/") ? "" : "/") + filePath.substring(5);
            }

            Path path       = Paths.get(translatedPath).normalize().toAbsolutePath();
            Path tmpBase    = Paths.get(tmpDir).normalize().toAbsolutePath();
            Path desktopBase = Paths.get(System.getProperty("user.home"), "Desktop")
                                    .normalize().toAbsolutePath();

            if (!path.startsWith(tmpBase) && !path.startsWith(desktopBase)) {
                log.warn("File serve rejected — path outside allowed dirs: {}", path);
                return ResponseEntity.badRequest().build();
            }

            File file = path.toFile();

            // If not found at exact path, search by filename in sap-mcp subtree
            if (!file.exists() || !file.isFile()) {
                String filename = path.getFileName().toString();
                Path sapMcpDir = tmpBase.resolve("sap-mcp");
                if (sapMcpDir.toFile().exists()) {
                    try (java.util.stream.Stream<Path> stream = java.nio.file.Files.walk(sapMcpDir)) {
                        java.util.Optional<Path> found = stream
                                .filter(p -> p.getFileName().toString().equals(filename))
                                .findFirst();
                        if (found.isPresent()) {
                            file = found.get().toFile();
                            log.info("File resolved by name search: {}", file.getAbsolutePath());
                        }
                    }
                }
            }

            if (!file.exists() || !file.isFile()) {
                return ResponseEntity.notFound().build();
            }

            String filename = path.getFileName().toString();
            MediaType mediaType = guessMediaType(filename);
            String disposition = (asAttachment ? "attachment" : "inline")
                    + "; filename=\"" + filename + "\"";

            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .body(resource);

        } catch (Exception e) {
            log.error("File serve failed for path: {}", filePath, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /** Health/status endpoint */
    @GetMapping("/api/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> info = chatService.getStatus();
        return ResponseEntity.ok(info);
    }

    private @org.springframework.lang.NonNull MediaType guessMediaType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf"))  return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".xlsx")) return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        if (lower.endsWith(".pptx")) return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        if (lower.endsWith(".docx")) return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}

