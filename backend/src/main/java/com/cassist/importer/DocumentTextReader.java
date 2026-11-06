package com.cassist.importer;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.Locale;
import java.util.zip.*;
import javax.xml.stream.*;

/** Reads text only. Never extracts files to disk or resolves external XML resources. */
public final class DocumentTextReader {
    private static final int MAX_UPLOAD=5*1024*1024, MAX_EXPANDED=8*1024*1024;
    public String read(String filename, byte[] data, String sourceType) {
        if(data.length==0 || data.length>MAX_UPLOAD) throw new IllegalArgumentException("文件为空或超过 5 MB");
        String name=filename==null?"":filename.toLowerCase(Locale.ROOT);
        if("TXT".equals(sourceType) && name.endsWith(".txt")) return text(data);
        if("JSON".equals(sourceType) && name.endsWith(".json")) return text(data);
        if("DOCX".equals(sourceType) && name.endsWith(".docx")) return docx(data);
        throw new IllegalArgumentException("请选择 TXT、DOCX 或 JSON 文件，文件类型必须匹配");
    }
    private String text(byte[] data) {
        Charset encoding=StandardCharsets.UTF_8; int offset=0;
        if(data.length>=3 && data[0]==(byte)0xef && data[1]==(byte)0xbb && data[2]==(byte)0xbf) offset=3;
        else if(data.length>=2 && data[0]==(byte)0xff && data[1]==(byte)0xfe) {encoding=StandardCharsets.UTF_16LE;offset=2;}
        else if(data.length>=2 && data[0]==(byte)0xfe && data[1]==(byte)0xff) {encoding=StandardCharsets.UTF_16BE;offset=2;}
        try { return encoding.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data,offset,data.length-offset)).toString(); }
        catch(CharacterCodingException e) {throw new IllegalArgumentException("文本编码无法识别，请另存为 UTF-8");}
    }
    private String docx(byte[] data) {
        byte[] document=null; int total=0,entries=0;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                if(++entries>256) throw new IllegalArgumentException("DOCX 文件条目过多");
                ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int count;
                while((count=zip.read(buffer))!=-1) {
                    total+=count;
                    if(total>MAX_EXPANDED) throw new IllegalArgumentException("DOCX 解压内容超过允许大小");
                    if("word/document.xml".equals(entry.getName())) out.write(buffer,0,count);
                }
                if("word/document.xml".equals(entry.getName())) {
                    if(document!=null) throw new IllegalArgumentException("DOCX 文档结构重复");
                    document=out.toByteArray();
                }
            }
        } catch(IOException e) {throw new IllegalArgumentException("DOCX 文件损坏或不是有效文档");}
        if(document==null) throw new IllegalArgumentException("DOCX 缺少正文");
        XMLInputFactory factory=XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD,false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities",false);
        factory.setXMLResolver((a,b,c,d)->{throw new XMLStreamException("External resource denied");});
        try {
            XMLStreamReader xml=factory.createXMLStreamReader(new ByteArrayInputStream(document));
            StringBuilder text=new StringBuilder(); boolean inText=false; int depth=0;
            while(xml.hasNext()) {
                int event=xml.next();
                if(event==XMLStreamConstants.DTD || event==XMLStreamConstants.ENTITY_REFERENCE) throw new IllegalArgumentException("DOCX 包含不支持的文档声明");
                if(event==XMLStreamConstants.START_ELEMENT) {
                    if(++depth>128) throw new IllegalArgumentException("DOCX 正文层级过深");
                    String name=xml.getLocalName();
                    if("t".equals(name)) inText=true;
                    else if("tab".equals(name)) text.append('\t');
                    else if("br".equals(name)) text.append('\n');
                } else if(event==XMLStreamConstants.CHARACTERS && inText) text.append(xml.getText());
                else if(event==XMLStreamConstants.END_ELEMENT) {
                    String name=xml.getLocalName(); --depth;
                    if("t".equals(name)) inText=false;
                    if("p".equals(name)) text.append('\n');
                }
            }
            xml.close();
            return text.toString();
        } catch(XMLStreamException e) {throw new IllegalArgumentException("DOCX 正文格式无效");}
    }
}
