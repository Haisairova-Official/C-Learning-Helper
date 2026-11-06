package com.cassist.importer;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import org.junit.jupiter.api.Test;

class DocumentTextReaderTest {
    private final DocumentTextReader reader=new DocumentTextReader();
    @Test void readsJsonWithBomAndRequiresMatchingFileType() {
        assertEquals("{\"problems\":[]}",reader.read("题目.json","\ufeff{\"problems\":[]}".getBytes(StandardCharsets.UTF_8),"JSON"));
        assertThrows(IllegalArgumentException.class,()->reader.read("题目.txt",new byte[]{65},"JSON"));
    }
    @Test void readsUtf8WithBomAndPreservesCode() {
        String text="\ufeff题干：求和\n参考答案：\n  printf(\"%d\\n\", a+b);";
        assertEquals(text.substring(1),reader.read("练习.txt",text.getBytes(StandardCharsets.UTF_8),"TXT"));
    }
    @Test void extractsDocxParagraphsAndTableText() throws Exception {
        String xml="<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:body><w:p><w:r><w:t>第1题：求和</w:t></w:r></w:p><w:tbl><w:tr><w:tc><w:p><w:r><w:t>输入样例：1 2</w:t></w:r></w:p></w:tc></w:tr></w:tbl></w:body></w:document>";
        assertEquals("第1题：求和\n输入样例：1 2\n",reader.read("题目.docx",zip(xml),"DOCX"));
    }
    @Test void refusesExternalEntities() throws Exception {
        String xml="<!DOCTYPE doc [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><doc>&x;</doc>";
        assertThrows(IllegalArgumentException.class,()->reader.read("题.docx",zip(xml),"DOCX"));
    }
    @Test void rejectsExpansionBomb() throws Exception {
        assertThrows(IllegalArgumentException.class,()->reader.read("题.docx",zip(" ".repeat(9*1024*1024)),"DOCX"));
    }
    @Test void rejectsInvalidEncodingAndMismatchedExtension() {
        assertThrows(IllegalArgumentException.class,()->reader.read("题.txt",new byte[]{(byte)0xff},"TXT"));
        assertThrows(IllegalArgumentException.class,()->reader.read("题.exe",new byte[]{65},"TXT"));
    }
    private byte[] zip(String xml) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(out)) {zip.putNextEntry(new ZipEntry("word/document.xml"));zip.write(xml.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
        return out.toByteArray();
    }
}
