package com.cassist.roster;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RosterParserTest {
    private final RosterParser parser = new RosterParser();
    @Test void titleBeforeReorderedCsvHeaderAndQuotedName() {
        var value=parser.parse("学生名单\n姓名,班级,学号,账号,角色\n\"Li, Ming\",计算机一班,00123,li_ming,ADMIN\n");
        var row=value.rows().getFirst();
        assertEquals("00123",row.studentNumber());assertEquals("Li, Ming",row.name());
        assertEquals("li_ming",row.username());assertEquals(3,row.rowNumber());
        assertEquals(1,value.warnings().size());
    }
    @Test void acceptsTabsWhitespaceLabelsAndUtf16() {
        assertEquals("张三",parser.parse("姓名\t学号\n张三\t00123").rows().getFirst().name());
        assertEquals("00123",parser.parse("张三 00123").rows().getFirst().studentNumber());
        assertEquals("计算机 1 班",parser.parse("学号：00123 姓名：张三 班级：计算机 1 班").rows().getFirst().className());
        assertEquals("00123",parser.read("学生.txt","学号 姓名\n00123 张三".getBytes(StandardCharsets.UTF_16)).rows().getFirst().studentNumber());
    }
    @Test void xlsxPreservesLeadingZeroAndPhysicalRowNumber() throws Exception {
        try(var workbook=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet();var header=sheet.createRow(0);
            header.createCell(0).setCellValue("学号");header.createCell(1).setCellValue("姓名");
            var row=sheet.createRow(4);var cell=row.createCell(0);cell.setCellValue(123);
            var style=workbook.createCellStyle();style.setDataFormat(workbook.createDataFormat().getFormat("00000"));cell.setCellStyle(style);
            row.createCell(1).setCellValue("张三");workbook.write(out);
            var parsed=parser.read("名单.xlsx",out.toByteArray()).rows().getFirst();
            assertEquals("00123",parsed.studentNumber());assertEquals(5,parsed.rowNumber());
        }
    }
    @Test void rejectsSpreadsheetFormulas() throws Exception {
        try(var workbook=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet();sheet.createRow(0).createCell(0).setCellFormula("1+1");workbook.write(out);
            assertTrue(assertThrows(IllegalArgumentException.class,()->parser.read("名单.xlsx",out.toByteArray())).getMessage().contains("公式"));
        }
    }
    @Test void docxTableRecognizesHeader() throws Exception {
        try(var document=new XWPFDocument();var out=new ByteArrayOutputStream()) {
            var table=document.createTable(2,2);table.getRow(0).getCell(0).setText("学号");table.getRow(0).getCell(1).setText("姓名");
            table.getRow(1).getCell(0).setText("00123");table.getRow(1).getCell(1).setText("张三");document.write(out);
            assertEquals("张三",parser.read("名单.docx",out.toByteArray()).rows().getFirst().name());
        }
    }
    @Test void rejectsMalformedAndOversizedInputs() throws Exception {
        assertThrows(IllegalArgumentException.class,()->parser.parse("学号,姓名\n123,\"张三"));
        assertThrows(IllegalArgumentException.class,()->parser.parse("学号 姓名 学号\n123 张三 456"));
        assertThrows(IllegalArgumentException.class,()->parser.parse("学号 姓名\n"+"123 张三\n".repeat(201)));
        assertThrows(IllegalArgumentException.class,()->parser.read("名单.xls",new byte[]{1}));
        assertThrows(IllegalArgumentException.class,()->parser.read("名单.docx",new byte[]{1}));
        try(var out=new ByteArrayOutputStream()) {
            try(var zip=new ZipOutputStream(out)){zip.putNextEntry(new ZipEntry("oversize"));zip.write(new byte[8*1024*1024+1]);zip.closeEntry();}
            assertThrows(IllegalArgumentException.class,()->parser.read("名单.xlsx",out.toByteArray()));
        }
    }
}
