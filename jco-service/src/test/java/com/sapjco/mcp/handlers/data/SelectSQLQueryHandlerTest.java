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

class SelectSQLQueryHandlerTest extends BaseHandlerTest {

    @InjectMocks
    private SelectSQLQueryHandler handler;

    private JcoSession mockTempSession;

    private static final String QUERY_RESULT_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <dataPreview:tableData xmlns:dataPreview="http://www.sap.com/adt/dataPreview">
                <dataPreview:columns>
                    <dataPreview:metadata dataPreview:name="CARRID" dataPreview:type="C"/>
                    <dataPreview:dataSet>
                        <dataPreview:data>LH</dataPreview:data>
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
    void selectSQL_success() throws Exception {
        setupTempSession();
        setupExecuteInContext(QUERY_RESULT_XML);

        CallToolRequest request = requestBuilder()
                .arg("query", "SELECT * FROM SFLIGHT")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        assertContains(result, "SQL Query Results");
        verify(jcoSessionManager).destroySession(TEST_TEMP_SESSION_ID);
    }

    @Test
    void selectSQL_withCTE_success() throws Exception {
        setupTempSession();
        setupExecuteInContext(QUERY_RESULT_XML);

        CallToolRequest request = requestBuilder()
                .arg("query", "WITH flights AS (SELECT * FROM SFLIGHT) SELECT * FROM flights")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
    }

    @Test
    void selectSQL_withRawResponse() throws Exception {
        setupTempSession();
        setupExecuteInContext(QUERY_RESULT_XML);

        CallToolRequest request = requestBuilder()
                .arg("query", "SELECT * FROM SFLIGHT")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertSuccess(result);
        // File path is returned in metadata
        assertContains(result, "File:");
        assertContains(result, "bytes");
    }

    @Test
    void selectSQL_missingQuery_returnsError() {
        CallToolRequest request = requestBuilder().build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "query is required");
    }

    @Test
    void selectSQL_insertQuery_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("query", "INSERT INTO SFLIGHT VALUES ('AA')")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "SELECT or WITH");
    }

    @Test
    void selectSQL_updateQuery_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("query", "UPDATE SFLIGHT SET PRICE = 100")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "SELECT or WITH");
    }

    @Test
    void selectSQL_deleteQuery_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("query", "DELETE FROM SFLIGHT")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "SELECT or WITH");
    }

    @Test
    void selectSQL_dangerousKeyword_returnsError() {
        CallToolRequest request = requestBuilder()
                .arg("query", "SELECT * FROM SFLIGHT; DROP TABLE SFLIGHT")
                .build();

        CallToolResult result = handler.handle(exchange, request);

        assertError(result);
        assertContains(result, "prohibited keywords");
    }

    @Test
    void selectSQL_executionError_returnsError() throws Exception {
        setupTempSession();
        when(jcoSessionManager.executeInContext(eq(TEST_TEMP_SESSION_ID), any()))
                .thenThrow(new RuntimeException("SQL error"));

        CallToolRequest request = requestBuilder()
                .arg("query", "SELECT * FROM INVALID_TABLE")
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
