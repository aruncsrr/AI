package com.sapjco.mcp.handlers.data;

import com.sapjco.mcp.handlers.BaseHandlerTest;
import com.sapjco.mcp.model.CreateSessionRequest;
import com.sapjco.mcp.model.JcoSession;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PreviewTableDataHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private PreviewTableDataHandler handler;

    private JcoSession mockTempSession;

    private static final String TABLE_DATA_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <dataPreview:tableData xmlns:dataPreview="http://www.sap.com/adt/dataPreview">
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="CARRID" dataPreview:type="C" 
                            dataPreview:description="Carrier ID" dataPreview:keyAttribute="true"/>
                    <dataPreview:dataSet>
                        <dataPreview:data>LH</dataPreview:data>
                        <dataPreview:data>AA</dataPreview:data>
                    </dataPreview:dataSet>
                </dataPreview:columns>
            </dataPreview:tableData>
            """;

    @BeforeEach
    void setUp() throws Exception {
        mockGetSystem();
        mockTempSession = createMockSession(TEST_TEMP_SESSION_ID, TEST_SYSTEM_ID);
    }

    @Test
    void previewTableData_success() throws Exception {
        setupTempSession();
        setupExecuteInContext(TABLE_DATA_XML);

        CallToolRequest request = requestBuilder()
                .arg("table_name", "SFLIGHT")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "Table Data");
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void previewTableData_withRowCount() throws Exception {
        setupTempSession();
        setupExecuteInContext(TABLE_DATA_XML);

        CallToolRequest request = requestBuilder()
                .arg("table_name", "SFLIGHT")
                .arg("row_count", 50)
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    @Test
    void previewTableData_withRawResponse() throws Exception {
        setupTempSession();
        setupExecuteInContext(TABLE_DATA_XML);

        CallToolRequest request = requestBuilder()
                .arg("table_name", "SFLIGHT")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        // File path is returned in metadata
        assertContains(result, "File:");
        assertContains(result, "bytes");
    }

    @Test
    void previewTableData_missingTableName_returnsError() {
        CallToolRequest request = requestBuilder().build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "table_name is required");
    }

    @Test
    void previewTableData_executionError_returnsError() throws Exception {
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("Table not found"));

        CallToolRequest request = requestBuilder()
                .arg("table_name", "NONEXISTENT")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    private void setupTempSession() throws Exception {
        lenient().when(jcoSessionManager.createSession(any(CreateSessionRequest.class)))
                .thenReturn(TEST_TEMP_SESSION_ID);
        lenient().when(jcoSessionManager.getSession(TEST_TEMP_SESSION_ID)).thenReturn(mockTempSession);
    }

    private void setupExecuteInContext(String returnValue) throws Exception {
        lenient().when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenReturn(returnValue);
    }
}
