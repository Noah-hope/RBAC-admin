package com.adgn.importfixture;

import com.adgn.backend.imports.ImportFileParser;
import com.adgn.backend.identity.security.IdentityFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** 上传内容属于不可信输入；每个夹具独立验证明确拒绝或原始值保留。 */
class ImportParserMatrixTest {
    @TempDir Path directory;
    private final ImportFileParser parser=new ImportFileParser();
    private static final String MIME="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private Path book(Map<String,String> parts)throws Exception{return ImportFileParserTest.zip(directory.resolve(UUID.randomUUID()+".xlsx"),parts);}
    private Map<String,String> parts(String rows){return ImportFileParserTest.packageParts(ImportFileParserTest.sheet(rows));}
    private void rejects(Map<String,String> parts)throws Exception{
        Path file=book(parts);byte[] before=Files.readAllBytes(file);
        assertThatThrownBy(()->parser.inspect(file,"data.xlsx",MIME)).isInstanceOfSatisfying(IdentityFailure.class,e->assertThat(e.status()).isEqualTo(422));
        assertThat(Files.readAllBytes(file)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={""," ","/absolute.xml","a\\b.xml","a:b.xml","a//b.xml","a/./b.xml","a/../b.xml","a/\u0001b.xml","xl/vbaProject.bin","EncryptedPackage"})
    void unsafeZipEntryNeverBecomesAnImportPath(String name)throws Exception{
        var parts=parts("<row r=\"1\"><c><v>safe</v></c></row>");parts.put(name,"<fixture/>");rejects(parts);
    }
    @ParameterizedTest @ValueSource(strings={"XFE1","A","a1","A2","A1000000000","1"})
    void invalidCellCoordinatesCannotBeSilentlyRepositioned(String reference)throws Exception{
        rejects(parts("<row r=\"1\"><c r=\""+reference+"\"><v>value</v></c></row>"));
    }
    @ParameterizedTest @ValueSource(strings={
        "<c><v>outside</v></c>","<row><row><c><v>nested</v></c></row></row>",
        "<row><c><c><v>nested</v></c></c></row>","<row r=\"0\"><c><v>zero</v></c></row>",
        "<row r=\"1048577\"><c><v>overflow</v></c></row>","<row><c t=\"b\"><v>2</v></c></row>",
        "<row><c t=\"s\"><v>-1</v></c></row>","<row><c t=\"unsupported\"><v>x</v></c></row>"})
    void invalidWorksheetStructureAndTypesAreRejected(String rows)throws Exception{rejects(parts(rows));}
    @ParameterizedTest @ValueSource(strings={"worksheets/a%20b.xml","worksheets/a?x=1","worksheets/a#fragment","../../../outside.xml"})
    void ambiguousRelationshipTargetsAreRejected(String target)throws Exception{
        var parts=parts("<row><c><v>x</v></c></row>");parts.put("xl/_rels/workbook.xml.rels",parts.get("xl/_rels/workbook.xml.rels").replace("worksheets/sheet1.xml",target));rejects(parts);
    }
    @ParameterizedTest @ValueSource(strings={"Id=\"r1\"","Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\"","Target=\"worksheets/sheet1.xml\""})
    void missingRelationshipFieldsDoNotDefaultToAnotherResource(String attribute)throws Exception{
        var parts=parts("<row><c><v>x</v></c></row>");parts.put("xl/_rels/workbook.xml.rels",parts.get("xl/_rels/workbook.xml.rels").replace(attribute,""));rejects(parts);
    }
    @Test void cellBudgetRejectsAnOversizedBlankWorkbookBeforeItCanConsumeUnboundedMemory()throws Exception{
        String rows=("<row>"+"<c/>".repeat(16384)+"</row>").repeat(61)+"<row>"+"<c/>".repeat(577)+"</row>";
        rejects(parts(rows)); // 共1000001个空单元格，压缩后仍须受总解析预算限制。
    }
    @Test void csvPreservesTabsLoneCarriageReturnsAndQuotedLineBreaks()throws Exception{
        Path file=directory.resolve("data.csv");Files.writeString(file,"key,value\r\"a\rb\",\tdata\r\"last\r\",end");
        var rows=parser.readSheet(file,"data.csv","CSV");assertThat(rows).hasSize(3);
        assertThat(rows.get(1).cells()).containsEntry(1,"a\rb").containsEntry(2,"\tdata");
        assertThat(rows.get(2).cells()).containsEntry(1,"last\r").containsEntry(2,"end");
    }
    @Test void cdataAndImplicitRowColumnNumbersRetainLiteralData()throws Exception{
        Path file=book(parts("<row><c t=\"str\"><v><![CDATA[<literal>]]></v></c><c t=\"d\"><v>2026-10-27</v></c><c t=\"n\"><v>-12</v></c></row>"));
        var rows=parser.readSheet(file,"data.xlsx","人员");assertThat(rows).hasSize(1);assertThat(rows.getFirst().number()).isEqualTo(1);
        assertThat(rows.getFirst().cells()).containsEntry(1,"<literal>").containsEntry(2,"2026-10-27").containsEntry(3,"-12");
    }
}
