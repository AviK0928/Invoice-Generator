package com.example.invoice.common.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

public class CsvUtil {

    public static void writeCsv(OutputStream out, String[] headers, List<String[]> rows) throws Exception {
        try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(out), CSVFormat.DEFAULT.withHeader(headers))) {
            for (String[] row : rows) {
                printer.printRecord((Object[]) row);
            }
        }
    }
}
