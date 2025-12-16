package com.kejicode.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EditFileToolTest {

    private EditFileTool tool;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        tool = new EditFileTool();
    }

    @Test
    void testEditSingleOccurrence() throws Exception {
        // Create a test file
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello World\nGoodbye World");

        // Edit the file
        String params = String.format(
            "{\"file_path\":\"%s\",\"old_string\":\"Hello\",\"new_string\":\"Hi\"}",
            testFile.toString().replace("\\", "\\\\")
        );
        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        String content = Files.readString(testFile);
        assertEquals("Hi World\nGoodbye World", content);
    }

    @Test
    void testEditMultipleOccurrencesWithReplaceAll() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "foo bar foo baz foo");

        String params = String.format(
            "{\"file_path\":\"%s\",\"old_string\":\"foo\",\"new_string\":\"bar\",\"replace_all\":true}",
            testFile.toString().replace("\\", "\\\\")
        );
        ToolResult result = tool.execute(params);

        assertTrue(result.isSuccess());
        String content = Files.readString(testFile);
        assertEquals("bar bar bar baz bar", content);
    }

    @Test
    void testEditMultipleOccurrencesWithoutReplaceAll() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "foo bar foo baz");

        String params = String.format(
            "{\"file_path\":\"%s\",\"old_string\":\"foo\",\"new_string\":\"bar\"}",
            testFile.toString().replace("\\", "\\\\")
        );
        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("appears"));
    }

    @Test
    void testEditNonExistentString() throws Exception {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "Hello World");

        String params = String.format(
            "{\"file_path\":\"%s\",\"old_string\":\"Goodbye\",\"new_string\":\"Hi\"}",
            testFile.toString().replace("\\", "\\\\")
        );
        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("not found"));
    }

    @Test
    void testEditNonExistentFile() throws Exception {
        Path nonExistent = tempDir.resolve("nonexistent.txt");

        String params = String.format(
            "{\"file_path\":\"%s\",\"old_string\":\"foo\",\"new_string\":\"bar\"}",
            nonExistent.toString().replace("\\", "\\\\")
        );
        ToolResult result = tool.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("does not exist"));
    }
}
