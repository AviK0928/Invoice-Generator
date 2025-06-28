package com.example.invoice.common.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipUtil {

    public static void zipFiles(List<File> files, OutputStream out) throws Exception {
        try (ZipOutputStream zipOut = new ZipOutputStream(out)) {
            for (File file : files) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    ZipEntry zipEntry = new ZipEntry(file.getName());
                    zipOut.putNextEntry(zipEntry);

                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = fis.read(buffer)) != -1) {
                        zipOut.write(buffer, 0, len);
                    }

                    zipOut.closeEntry();
                }
            }
        }
    }
}
