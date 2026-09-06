package com.example.invoice.common.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

public class CsvUtil {

    public static void writeCsv(OutputStream out, String[] headers, List<String[]> rows) throws Exception {
        // builder(), not the deprecated withHeader(...). setNullString keeps a
        // null cell empty rather than writing the text "null" into a customer
        // field.
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(headers)
                .setNullString("")
                .build();

        // The writer is only flushed when CSVPrinter closes it, so nothing
        // reaches `out` until this block exits.
        try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(out), format)) {
            for (String[] row : rows) {
                printer.printRecord((Object[]) row);
            }
        }
    }
}