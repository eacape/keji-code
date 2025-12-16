package com.kejicode.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReadFileToolTest {

    private ReadFileTool tool;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        tool = new ReadFileTool();
    }

    @Test
    void testReadExistingFile() throws Exception {
        // Create a test file
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Line 1\nLine 2\nLine 3");

        // Read the file
        String params = String.format("{\"file_path\":\"%s\"}", testFile.toString().replace("\\", "\\\\"));
        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("Line 1"));
        assertTrue(result.getOutput().contains("Line 2"));
        assertTrue(result.getOutput().contains("Line 3"));
    }

    @Test
    void testReadNonExistentFile() throws Exception {
        Path nonExistent = tempDir.resolve("nonexistent.txt");
        String params = String.format("{\"file_path\":\"%s\"}", nonExistent.toString().replace("\\", "\\\\"));

        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("does not exist"));
    }

    @Test
    void testReadWithOffset() throws Exception {
        // Create a test file with multiple lines
        Path testFile = tempDir.resolve("test.txt");
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            content.append("Line ").append(i).append("\n");
        }
        Files.writeString(testFile, content.toString());

        // Read with offset
        String params = String.format(
            "{\"file_path\":\"%s\",\"offset\":5,\"limit\":3}",
            testFile.toString().replace("\\", "\\\\")
        );
        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("Line 6"));
        assertTrue(result.getOutput().contains("Line 7"));
        assertTrue(result.getOutput().contains("Line 8"));
        assertFalse(result.getOutput().contains("Line 1"));
    }

    @Test
    void testReadDirectory() throws Exception {
        Path dir = tempDir.resolve("testdir");
        Files.createDirectory(dir);

        String params = String.format("{\"file_path\":\"%s\"}", dir.toString().replace("\\", "\\\\"));
        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("directory"));
    }

    @Test
    void testGetName() {
        assertEquals("Read", tool.getName());
    }

    @Test
    void testGetDescription() {
        assertNotNull(tool.getDescription());
        assertFalse(tool.getDescription().isEmpty());
    }
}
