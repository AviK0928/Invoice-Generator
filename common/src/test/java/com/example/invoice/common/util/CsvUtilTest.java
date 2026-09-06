package com.example.invoice.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSV escaping, which is where this kind of code goes wrong: a customer named
 * "Smith, John" or an address with a newline silently shifts every subsequent
 * column, and the file still opens. export-service writes customer names and
 * item descriptions straight from the database into these rows.
 */
class CsvUtilTest {

    @Test
    @DisplayName("headers and rows are written in order")
    void writesHeadersAndRows() throws Exception {
        String csv = write(new String[] { "Id", "Name" },
                List.of(new String[] { "1", "Test Co" }, new String[] { "2", "Other Co" }));

        assertThat(csv.lines()).containsExactly("Id,Name", "1,Test Co", "2,Other Co");
    }

    @Test
    @DisplayName("a value containing the delimiter is quoted")
    void quotesEmbeddedCommas() throws Exception {
        String csv = write(new String[] { "Name" },
                List.<String[]>of(new String[] { "Smith, John" }));

        // Unquoted this reads as two columns and shifts the rest of the row.
        assertThat(csv.lines()).containsExactly("Name", "\"Smith, John\"");
    }

    @Test
    @DisplayName("a value containing a quote has it doubled")
    void escapesEmbeddedQuotes() throws Exception {
        String csv = write(new String[] { "Name" },
                List.<String[]>of(new String[] { "The \"Big\" Co" }));

        assertThat(csv.lines()).containsExactly("Name", "\"The \"\"Big\"\" Co\"");
    }

    @Test
    @DisplayName("a value containing a newline stays one record")
    void quotesEmbeddedNewlines() throws Exception {
        String csv = write(new String[] { "Address" },
                List.<String[]>of(new String[] { "Line 1\nLine 2" }));

        assertThat(csv).contains("\"Line 1\nLine 2\"");
    }

    @Test
    @DisplayName("a null cell is written empty, not as the text null")
    void writesNullAsEmpty() throws Exception {
        // Collections.singletonList, not List.of: with a single String[]
        // argument List.of spreads the array into its varargs and yields a
        // List<String>, which is why every one-row case here needs care.
        String csv = write(new String[] { "A", "B" },
                Collections.singletonList(new String[] { "x", null }));

        assertThat(csv.lines()).containsExactly("A,B", "x,");
    }

    @Test
    @DisplayName("no rows still writes the header")
    void writesHeaderWithNoRows() throws Exception {
        assertThat(write(new String[] { "Id", "Name" }, List.of()).lines())
                .containsExactly("Id,Name");
    }

    private String write(String[] headers, List<String[]> rows) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvUtil.writeCsv(out, headers, rows);
        // Nothing reaches the stream until CSVPrinter closes the writer. If
        // that ever changes, this sees an empty buffer.
        return out.toString(StandardCharsets.UTF_8);
    }
}