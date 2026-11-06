package com.cassist.roster;

import com.cassist.importer.DocumentTextReader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;

public final class RosterParser {
    public record Row(int rowNumber,String studentNumber,String name,String username,String className) {}
    public record Parsed(List<Row> rows,List<String> warnings) {}
    private static final int MAX_ROWS=200, MAX_TEXT=1024*1024;
    private static final Map<String,String> ALIASES=Map.ofEntries(
        Map.entry("学号","studentNumber"),Map.entry("学生学号","studentNumber"),Map.entry("studentnumber","studentNumber"),Map.entry("studentid","studentNumber"),
        Map.entry("姓名","name"),Map.entry("学生姓名","name"),Map.entry("name","name"),Map.entry("displayname","name"),
        Map.entry("账号","username"),Map.entry("登录名","username"),Map.entry("登录账号","username"),Map.entry("username","username"),
        Map.entry("班级","className"),Map.entry("班级名称","className"),Map.entry("classname","className"),Map.entry("class","className"));
    public Parsed parse(String text) {
        if(text==null || text.isBlank() || text.getBytes(StandardCharsets.UTF_8).length>MAX_TEXT) throw new IllegalArgumentException("请提供不超过 1 MiB 的学生名单");
        text=text.replace("\uFEFF","").replace("\r\n","\n").replace('\r','\n');
        return table(delimited(text));
    }
    public Parsed read(String filename,byte[] data) {
        if(data.length==0 || data.length>5*1024*1024) throw new IllegalArgumentException("文件为空或超过 5 MB");
        String name=filename==null?"":filename.toLowerCase(Locale.ROOT);
        if(name.endsWith(".txt") || name.endsWith(".csv") || name.endsWith(".tsv")) return parse(new DocumentTextReader().read("roster.txt",data,"TXT"));
        if(!name.endsWith(".xlsx") && !name.endsWith(".docx")) throw new IllegalArgumentException("请选择 XLSX、DOCX、CSV、TSV 或 TXT 文件");
        checkZip(data);
        List<List<String>> rows=new ArrayList<>();
        try {
            if(name.endsWith(".xlsx")) {
                try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(data))) {
                    if(workbook.getNumberOfSheets()!=1) throw new IllegalArgumentException("请在文件中仅保留需要导入的一个工作表");
                    Sheet sheet=workbook.getSheetAt(0);
                    if(sheet.getLastRowNum()>1000) throw new IllegalArgumentException("表格行数过多，请删除多余空行或分批导入");
                    DataFormatter formatter=new DataFormatter(Locale.ROOT);
                    for(org.apache.poi.ss.usermodel.Row row:sheet) {
                        while(rows.size()<row.getRowNum()) rows.add(List.of());
                        if(row.getLastCellNum()>50) throw new IllegalArgumentException("表格列数不能超过 50 列");
                        List<String> cells=new ArrayList<>();
                        for(int i=0;i<row.getLastCellNum();i++) {
                            Cell cell=row.getCell(i);
                            if(cell!=null && cell.getCellType()==CellType.FORMULA) throw new IllegalArgumentException("第 "+(row.getRowNum()+1)+" 行含公式，请先粘贴为值");
                            cells.add(cell==null?"":formatter.formatCellValue(cell));
                        }
                        rows.add(cells);
                    }
                }
            } else {
                try(var document=new XWPFDocument(new ByteArrayInputStream(data))) {
                    for(IBodyElement element:document.getBodyElements()) {
                        if(element instanceof XWPFTable table) {
                            for(XWPFTableRow row:table.getRows()) rows.add(row.getTableCells().stream().map(c->c.getText().strip()).toList());
                        } else if(element instanceof XWPFParagraph paragraph && !paragraph.getText().isBlank()) rows.addAll(delimited(paragraph.getText()));
                        if(rows.size()>1000) throw new IllegalArgumentException("名单行数过多，请分批导入");
                    }
                }
            }
        } catch(IllegalArgumentException e) { throw e; }
        catch(Exception e) { throw new IllegalArgumentException("文件无法读取，请检查格式或另存后重试"); }
        return table(rows);
    }
    private void checkZip(byte[] data) {
        long total=0; int count=0; Set<String> names=new HashSet<>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry; byte[] buffer=new byte[8192];
            while((entry=zip.getNextEntry())!=null) {
                if(++count>256 || !names.add(entry.getName())) throw new IllegalArgumentException("文件结构异常或条目过多");
                int n; while((n=zip.read(buffer))!=-1) { total+=n; if(total>8*1024*1024) throw new IllegalArgumentException("文件解压内容过大，请分批导入"); }
            }
            if(count==0) throw new IllegalArgumentException("文件格式无效");
        } catch(IOException e) { throw new IllegalArgumentException("文件损坏或格式无效"); }
    }
    private String alias(String value) { return ALIASES.getOrDefault(value.strip().toLowerCase(Locale.ROOT).replaceAll("[ _：:]",""),""); }
    private Parsed table(List<List<String>> lines) {
        if(lines.size()>1000) throw new IllegalArgumentException("名单行数过多，请分批导入");
        int header=-1; Map<Integer,String> mapping=new LinkedHashMap<>(); List<String> warnings=new ArrayList<>();
        for(int i=0;i<Math.min(lines.size(),20);i++) {
            List<String> line=lines.get(i);
            if(line.stream().anyMatch(c->alias(c).equals("studentNumber")) && line.stream().anyMatch(c->alias(c).equals("name"))) {
                header=i;
                for(int j=0;j<line.size();j++) { String key=alias(line.get(j)); if(!key.isEmpty()) { if(mapping.containsValue(key)) throw new IllegalArgumentException("表头字段重复，请保留唯一的学号、姓名、账号和班级列"); mapping.put(j,key); } }
                if(mapping.size()<line.size()) warnings.add("仅识别学号、姓名、账号和班级，其余列未导入。");
                break;
            }
        }
        if(header<0) warnings.add("未找到表头，已按常见名单格式识别，请核对各列。");
        List<Row> rows=new ArrayList<>(); int bytes=0;
        for(int i=header+1;i<lines.size();i++) {
            List<String> line=lines.get(i);
            if(line.stream().allMatch(String::isBlank)) continue;
            for(String cell:line) { bytes+=cell.length(); if(cell.length()>2000 || bytes>MAX_TEXT) throw new IllegalArgumentException("名单内容过长"); }
            Map<String,String> fields=new HashMap<>();
            if(header>=0) {
                for(var column:mapping.entrySet()) fields.put(column.getValue(),column.getKey()<line.size()?line.get(column.getKey()).strip():"");
            } else {
                String raw=String.join(" ",line);
                Matcher labeled=Pattern.compile("(学号|学生学号|姓名|学生姓名|账号|登录名|登录账号|班级|班级名称)\\s*[:：]\\s*(.*?)(?=\\s+(?:学号|学生学号|姓名|学生姓名|账号|登录名|登录账号|班级|班级名称)\\s*[:：]|$)").matcher(raw);
                while(labeled.find()) fields.put(alias(labeled.group(1)),labeled.group(2).strip());
                if(fields.isEmpty()) {
                    List<String> cells=line.stream().filter(c->!c.isBlank()).toList();
                    int numberIndex=cells.size()>1 && !cells.get(0).matches(".*[0-9].*") && cells.get(1).matches(".*[0-9].*")?1:0;
                    fields.put("studentNumber",cells.size()>numberIndex?cells.get(numberIndex).strip():"");
                    fields.put("name",cells.size()>1?cells.get(1-numberIndex).strip():"");
                    if(cells.size()>2) fields.put("className",cells.get(2).strip());
                    if(cells.size()>3) fields.put("username",cells.get(3).strip());
                }
            }
            rows.add(new Row(i+1,fields.getOrDefault("studentNumber",""),fields.getOrDefault("name",""),fields.getOrDefault("username",""),fields.getOrDefault("className","")));
            if(rows.size()>MAX_ROWS) throw new IllegalArgumentException("每次最多导入 200 名学生");
        }
        if(rows.isEmpty()) throw new IllegalArgumentException("未识别到学生，请提供学号和姓名");
        return new Parsed(rows,warnings);
    }
    private List<List<String>> delimited(String text) {
        String first=text.lines().limit(20).filter(s->s.contains("\t") || s.contains(",") || s.contains("，") || s.contains("|") || s.contains(";")).findFirst()
            .orElseGet(()->text.lines().filter(s->!s.isBlank()).findFirst().orElse(""));
        char delimiter=first.contains("\t")?'\t':first.contains(",")?',':first.contains("，")?'，':first.contains("|")?'|':first.contains(";")?';':' ';
        if(delimiter==' ') return text.lines().map(line->Arrays.asList(line.strip().split("[\\s\\u3000]+"))).toList();
        List<List<String>> rows=new ArrayList<>(); List<String> row=new ArrayList<>(); StringBuilder cell=new StringBuilder(); boolean quoted=false;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(c=='"') { if(quoted && i+1<text.length() && text.charAt(i+1)=='"') {cell.append('"');i++;} else quoted=!quoted; }
            else if(!quoted && (c==delimiter || c=='\n')) {row.add(cell.toString().strip());cell.setLength(0);if(c=='\n'){rows.add(row);row=new ArrayList<>();}}
            else cell.append(c);
        }
        if(quoted) throw new IllegalArgumentException("引号未闭合，请检查 CSV 内容");
        row.add(cell.toString().strip()); if(row.stream().anyMatch(s->!s.isBlank())) rows.add(row);
        return rows;
    }
}
