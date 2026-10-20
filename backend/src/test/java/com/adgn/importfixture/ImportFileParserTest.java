package com.adgn.importfixture;

import com.adgn.backend.imports.ImportFileParser;
import com.adgn.backend.identity.security.IdentityFailure;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

class ImportFileParserTest {
    @TempDir Path directory;
    private final ImportFileParser parser=new ImportFileParser();
    private Path csv(String value)throws Exception{Path p=directory.resolve(UUID.randomUUID()+".csv");Files.writeString(p,value,StandardCharsets.UTF_8);return p;}
    private void rejected(Path p,String name,String mime,int status){assertThatThrownBy(()->parser.inspect(p,name,mime)).isInstanceOfSatisfying(IdentityFailure.class,e->assertThat(e.status()).isEqualTo(status));}
    @Test void csvPreservesEscapedQuotesCommaNewlinesLeadingZeroAndTrailingEmptyCell()throws Exception{
        Path p=csv("\ufeffkey,name,note,empty\r\n007,\"王,明\",\"line1\nline2 \"\"quoted\"\"\",\r\n");
        assertThat(parser.inspect(p,"people.csv","text/csv").sheetNames()).containsExactly("CSV");
        var rows=parser.readSheet(p,"people.csv","CSV");assertThat(rows).hasSize(2);
        assertThat(rows.get(1).number()).isEqualTo(2);
        assertThat(rows.get(1).cells()).containsEntry(1,"007").containsEntry(2,"王,明").containsEntry(3,"line1\nline2 \"quoted\"").containsEntry(4,"");
        assertThatThrownBy(()->rows.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(()->rows.get(1).cells().put(1,"tampered")).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void csvAcceptsExactlyOneThousandDataRowsAndRejectsTheNext()throws Exception{
        String rows="key,name\n"+"user,Person\n".repeat(1000);
        assertThat(parser.readSheet(csv(rows),"data.csv","CSV")).hasSize(1001);
        rejected(csv(rows+"extra,Person\n"),"data.csv","text/csv",413);
    }
    @Test void csvRejectsMalformedQuotingUtf8FormulaAndWrongMimeOrExtension()throws Exception{
        for(String s:List.of("a,b\n\"unterminated,b\n","a,b\n\"closed\"junk,b\n","a,b\nx\"y,z\n","a,b\n=SUM(1),x\n","\n\r\n"))rejected(csv(s),"data.csv","text/csv",422);
        Path bad=directory.resolve("bad.csv");Files.write(bad,new byte[]{(byte)0xc3,0x28});rejected(bad,"bad.csv","text/csv",422);
        rejected(csv("a,b\nx,y\n"),"data.csv","application/json",422);
        rejected(csv("a,b\nx,y\n"),"../data.csv","text/csv",422);
        rejected(csv("a,b\nx,y\n"),"data.xlsm","application/octet-stream",422);
        rejected(csv("a,b\nx,y\n"),"data.xlsx",XLSX,422);
    }
    @Test void negativeNumbersAreDataAndMissingOrAmbiguousSheetIsRejected()throws Exception{
        Path p=csv("key,value\na,-42\n");assertThat(parser.readSheet(p,"data.csv","CSV").get(1).cells()).containsEntry(2,"-42");
        assertThatThrownBy(()->parser.readSheet(p,"data.csv","other")).isInstanceOf(IdentityFailure.class);
    }
    static final String XLSX="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String NS="http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    static Map<String,String> packageParts(String sheet){
        var parts=new LinkedHashMap<String,String>();
        parts.put("[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/></Types>");
        parts.put("_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"r0\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
        parts.put("xl/workbook.xml","<workbook xmlns=\""+NS+"\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"人员\" sheetId=\"1\" r:id=\"r1\"/></sheets></workbook>");
        parts.put("xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"r1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
        parts.put("xl/worksheets/sheet1.xml",sheet);return parts;
    }
    static String sheet(String cells){return "<worksheet xmlns=\""+NS+"\"><dimension ref=\"A1:XFD1048576\"/><sheetData>"+cells+"</sheetData></worksheet>";}
    static Path zip(Path file,Map<String,String> entries)throws Exception{
        try(var out=new ZipOutputStream(Files.newOutputStream(file))){for(var e:entries.entrySet()){out.putNextEntry(new ZipEntry(e.getKey()));out.write(e.getValue().getBytes(StandardCharsets.UTF_8));out.closeEntry();}}return file;
    }
    private Path xlsx(Map<String,String> parts)throws Exception{return zip(directory.resolve(UUID.randomUUID()+".xlsx"),parts);}
    @Test void xlsxUsesActualRowsAndReadsSharedInlineAndBooleanWithoutExecutingAnything()throws Exception{
        var parts=packageParts(sheet("<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>key</t></is></c></row><row r=\"5\"><c r=\"A5\" t=\"s\"><v>0</v></c><c r=\"C5\" t=\"b\"><v>1</v></c></row>"));
        parts.put("xl/sharedStrings.xml","<sst xmlns=\""+NS+"\"><si><r><t>00</t></r><r><t>7</t></r></si></sst>");
        parts.put("xl/_rels/workbook.xml.rels",parts.get("xl/_rels/workbook.xml.rels").replace("</Relationships>","<Relationship Id=\"shared\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/></Relationships>"));
        Path p=xlsx(parts);assertThat(parser.inspect(p,"data.xlsx",XLSX).sheetNames()).containsExactly("人员");
        var rows=parser.readSheet(p,"data.xlsx","人员");assertThat(rows).hasSize(2);assertThat(rows.get(1).number()).isEqualTo(5);
        assertThat(rows.get(1).cells()).containsEntry(1,"007").containsEntry(3,"1");
    }
    @Test void xlsxRejectsFormulaIncludingCachedResult()throws Exception{
        rejected(xlsx(packageParts(sheet("<row r=\"1\"><c r=\"A1\"><f>1+1</f><v>2</v></c></row>"))),"data.xlsx",XLSX,422);
    }
    @Test void xlsxRejectsMacroExternalRelationshipAndDtd()throws Exception{
        var macro=packageParts(sheet("<row r=\"1\"><c r=\"A1\"><v>1</v></c></row>"));macro.put("xl/vbaProject.bin","not executed");rejected(xlsx(macro),"data.xlsx",XLSX,422);
        var external=packageParts(sheet(""));external.put("xl/_rels/workbook.xml.rels","<Relationships><Relationship Id=\"r1\" TargetMode=\"External\" Target=\"https://example.invalid/file\"/></Relationships>");rejected(xlsx(external),"data.xlsx",XLSX,422);
        var dtd=packageParts(sheet(""));dtd.put("xl/workbook.xml","<!DOCTYPE x [<!ENTITY leak SYSTEM 'file:///must-not-read'>]><workbook><sheets><sheet name=\"&leak;\"/></sheets></workbook>");rejected(xlsx(dtd),"data.xlsx",XLSX,422);
    }
    @Test void xlsxRejectsTraversalMissingSheetDuplicateNameAndSharedStringIndex()throws Exception{
        var traversal=packageParts(sheet(""));traversal.put("../outside","payload");rejected(xlsx(traversal),"data.xlsx",XLSX,422);
        var absent=packageParts(sheet(""));absent.remove("xl/worksheets/sheet1.xml");rejected(xlsx(absent),"data.xlsx",XLSX,422);
        var duplicate=packageParts(sheet(""));duplicate.put("xl/workbook.xml","<workbook xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"重复\" r:id=\"r1\"/><sheet name=\"重复\" r:id=\"r1\"/></sheets></workbook>");rejected(xlsx(duplicate),"data.xlsx",XLSX,422);
        rejected(xlsx(packageParts(sheet("<row r=\"1\"><c r=\"A1\" t=\"s\"><v>999</v></c></row>"))),"data.xlsx",XLSX,422);
    }
    @Test void xlsxBoundsActualUncompressedBytes()throws Exception{
        Path p=directory.resolve("large.xlsx");
        try(var out=new ZipOutputStream(Files.newOutputStream(p))){for(var e:packageParts(sheet("")).entrySet()){out.putNextEntry(new ZipEntry(e.getKey()));out.write(e.getValue().getBytes(StandardCharsets.UTF_8));out.closeEntry();}
            out.putNextEntry(new ZipEntry("padding.bin"));byte[] block=new byte[8192];for(int n=0;n<6401;n++)out.write(block);out.closeEntry();}
        assertThat(Files.size(p)).isLessThan(10*1024*1024);rejected(p,"data.xlsx",XLSX,413);
    }
    @Test void originalBytesLimitIsIndependentOfFileName()throws Exception{
        Path p=directory.resolve("oversize.csv");try(var out=Files.newOutputStream(p)){byte[] b=new byte[8192];for(int n=0;n<1281;n++)out.write(b);}rejected(p,"data.csv","text/csv",413);
    }
    @Test void xlsxRejectsNonWorkbookOrNonWorksheetRootsAndAmbiguousOfficeDocument()throws Exception{
        var wrong=packageParts(sheet("<row r=\"1\"><c r=\"A1\"><v>1</v></c></row>"));
        wrong.put("xl/workbook.xml",wrong.get("xl/workbook.xml").replace("<workbook ","<pretend ").replace("</workbook>","</pretend>"));rejected(xlsx(wrong),"data.xlsx",XLSX,422);
        var worksheet=packageParts(sheet("<row r=\"1\"><c r=\"A1\"><v>1</v></c></row>"));worksheet.put("xl/worksheets/sheet1.xml",worksheet.get("xl/worksheets/sheet1.xml").replace("<worksheet ","<pretend ").replace("</worksheet>","</pretend>"));rejected(xlsx(worksheet),"data.xlsx",XLSX,422);
        var ambiguous=packageParts(sheet(""));ambiguous.put("_rels/.rels",ambiguous.get("_rels/.rels").replace("</Relationships>","<Relationship Id=\"r2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"));rejected(xlsx(ambiguous),"data.xlsx",XLSX,422);
    }
    @Test void sharedStringsFollowTheRelationshipAndIgnorePhoneticAnnotations()throws Exception{
        var parts=packageParts(sheet("<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c></row>"));
        parts.put("xl/_rels/workbook.xml.rels",parts.get("xl/_rels/workbook.xml.rels").replace("</Relationships>","<Relationship Id=\"shared\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"../strings/data.xml\"/></Relationships>"));
        parts.put("strings/data.xml","<sst xmlns=\""+NS+"\"><si><t>007</t><rPh sb=\"0\" eb=\"3\"><t>phonetic</t></rPh></si></sst>");
        assertThat(parser.readSheet(xlsx(parts),"data.xlsx","人员").getFirst().cells()).containsEntry(1,"007");
    }
    @Test void xlsxEnforcesActualDataRowLimitAndRejectsEscapingRelationship()throws Exception{
        var content=new StringBuilder();for(int n=1;n<=1002;n++)content.append("<row r=\"").append(n).append("\"><c r=\"A").append(n).append("\"><v>1</v></c></row>");
        rejected(xlsx(packageParts(sheet(content.toString()))),"data.xlsx",XLSX,413);
        var parts=packageParts(sheet(""));parts.put("xl/_rels/workbook.xml.rels",parts.get("xl/_rels/workbook.xml.rels").replace("worksheets/sheet1.xml","../../../outside.xml"));rejected(xlsx(parts),"data.xlsx",XLSX,422);
    }
}
