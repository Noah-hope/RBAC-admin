package com.adgn.backend.imports;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.*;
import java.nio.ByteBuffer;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.net.URI;
import javax.xml.stream.*;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.core.transaction.PersistenceFailure;

/** Data-only CSV/XLSX parser; import mapping and business mutations belong to BE-29/30. */
public final class ImportFileParser {
    public record Inspection(List<String> sheetNames){public Inspection{sheetNames=List.copyOf(sheetNames);}}
    public record Row(int number,Map<Integer,String> cells){public Row{cells=Collections.unmodifiableMap(new LinkedHashMap<>(cells));}}
    public static final int MAX_FILE=10*1024*1024,MAX_EXPANDED=50*1024*1024;
    private static final String XLSX="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public Inspection inspect(Path file,String fileName,String mediaType){
        String extension=extension(fileName),mime=mediaType==null?"":mediaType.split(";",2)[0].strip().toLowerCase(Locale.ROOT);
        if(!(extension.equals("csv")?Set.of("text/csv","application/csv","application/vnd.ms-excel","application/octet-stream"):Set.of(XLSX,"application/octet-stream")).contains(mime))throw invalid();
        originalSize(file);
        if(extension.equals("csv")){csv(file,false);return new Inspection(List.of("CSV"));}
        return workbook(file,null).inspection();
    }
    public List<Row> readSheet(Path file,String fileName,String sheetName){
        String extension=extension(fileName);originalSize(file);
        if(extension.equals("csv")){if(!"CSV".equals(sheetName))throw invalid();return csv(file,true);}
        if(sheetName==null || sheetName.isBlank())throw invalid();return workbook(file,sheetName).rows();
    }
    public static String extension(String name){
        if(name==null || name.isBlank() || name.codePointCount(0,name.length())>200 || name.contains("/") || name.contains("\\") || name.chars().anyMatch(Character::isISOControl))throw invalid();
        int dot=name.lastIndexOf('.');String extension=dot<1?"":name.substring(dot+1).toLowerCase(Locale.ROOT);
        if(!Set.of("csv","xlsx").contains(extension))throw invalid();return extension;
    }
    private static void originalSize(Path path){
        try{long size=Files.size(path);if(size>MAX_FILE)throw large();if(size<1)throw invalid();}
        catch(IOException failure){throw unavailable();}
    }
    private static List<Row> csv(Path file,boolean collect){
        String text;
        try(var in=Files.newInputStream(file)){
            byte[] bytes=in.readNBytes(MAX_FILE+1);if(bytes.length>MAX_FILE)throw large();
            text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        }catch(CharacterCodingException bad){throw invalid();}catch(IOException failure){throw unavailable();}
        if(text.startsWith("\ufeff"))text=text.substring(1);
        var result=new ArrayList<Row>();var cells=new LinkedHashMap<Integer,String>();var field=new StringBuilder();
        boolean quoted=false,closed=false;int column=1,line=1,start=1,count=0;
        for(int i=0;i<=text.length();i++){
            char ch=i==text.length()?'\0':text.charAt(i);boolean eof=i==text.length();
            if(!eof && Character.isISOControl(ch) && ch!='\n' && ch!='\r' && ch!='\t')throw invalid();
            if(quoted){
                if(eof)throw invalid();
                if(ch=='"'){if(i+1<text.length() && text.charAt(i+1)=='"'){field.append('"');i++;}else{quoted=false;closed=true;}}
                else{field.append(ch);if(ch=='\n' || (ch=='\r' && (i+1==text.length() || text.charAt(i+1)!='\n')))line++;}
                continue;
            }
            if(!eof && ch=='"'){if(closed || !field.isEmpty())throw invalid();quoted=true;continue;}
            boolean newline=ch=='\n' || ch=='\r';
            if(eof || ch==',' || newline){
                String value=field.toString();formula(value);cells.put(column,value);field.setLength(0);closed=false;
                if(ch==',' && !eof){column++;if(column>16384)throw large();continue;}
                if(cells.values().stream().anyMatch(v->!v.isBlank())){if(++count>1001)throw large();if(collect)result.add(new Row(start,cells));}
                cells=new LinkedHashMap<>();column=1;
                if(newline){if(ch=='\r' && i+1<text.length() && text.charAt(i+1)=='\n')i++;line++;start=line;}
            }else{if(closed)throw invalid();field.append(ch);}
        }
        if(count==0)throw invalid();return List.copyOf(result);
    }
    private record Book(Inspection inspection,List<Row> rows) { }
    private record Sheet(String name,String path) { }
    private static Book workbook(Path file,String selected){
        var entries=entries(file);
        if(!entries.containsKey("[Content_Types].xml"))throw invalid();
        root(required(entries,"[Content_Types].xml"),"Types",Set.of("http://schemas.openxmlformats.org/package/2006/content-types"));
        for(var e:entries.entrySet())if(e.getKey().endsWith(".xml") || e.getKey().endsWith(".rels")){
            XMLStreamReader r=xml(e.getValue());
            try{while(r.hasNext()){int event=r.next();if(event==XMLStreamConstants.DTD)throw invalid();if(event!=XMLStreamConstants.START_ELEMENT)continue;
                String local=r.getLocalName();
                if(local.equals("f"))throw invalid();
                String type=attribute(r,"ContentType");if(type!=null && (type.toLowerCase(Locale.ROOT).contains("macro") || type.toLowerCase(Locale.ROOT).contains("vba")))throw invalid();
                if(local.equals("Relationship") && ("External".equalsIgnoreCase(attribute(r,"TargetMode")) || lower(attribute(r,"Type")).contains("external") || lower(attribute(r,"Type")).contains("vba")))throw invalid();
            }}catch(XMLStreamException bad){throw invalid();}finally{close(r);}
        }
        var documents=relationships(required(entries,"_rels/.rels"),"").values().stream().filter(v->v.type().endsWith("/officeDocument")).toList();
        if(documents.size()!=1)throw invalid();String workbookPath=documents.getFirst().path();
        root(required(entries,workbookPath),"workbook",SHEET_NAMESPACES);
        int slash=workbookPath.lastIndexOf('/');String prefix=slash<0?"":workbookPath.substring(0,slash+1);
        String name=workbookPath.substring(slash+1);
        var relations=relationships(required(entries,prefix+"_rels/"+name+".rels"),prefix);
        var sheets=new ArrayList<Sheet>();var names=new HashSet<String>();var paths=new HashSet<String>();
        XMLStreamReader r=xml(required(entries,workbookPath));
        try{while(r.hasNext()){if(r.next()!=XMLStreamConstants.START_ELEMENT)continue;
            if(r.getLocalName().equals("sheet")){
                String sheet=attribute(r,"name"),id=attribute(r,"id");var relation=relations.get(id);
                if(sheet==null || sheet.isBlank() || sheet.codePointCount(0,sheet.length())>100 || !names.add(sheet.toLowerCase(Locale.ROOT))
                    || relation==null || !relation.type().endsWith("/worksheet") || !paths.add(relation.path()))throw invalid();
                required(entries,relation.path());sheets.add(new Sheet(sheet,relation.path()));if(sheets.size()>100)throw large();
            }
        }}catch(XMLStreamException bad){throw invalid();}finally{close(r);}
        if(sheets.isEmpty() || (selected!=null && sheets.stream().noneMatch(s->s.name().equals(selected))))throw invalid();
        var stringParts=relations.values().stream().filter(v->v.type().endsWith("/sharedStrings")).toList();if(stringParts.size()>1)throw invalid();
        List<String> shared=sharedStrings(stringParts.isEmpty()?null:required(entries,stringParts.getFirst().path()));
        List<Row> rows=List.of();int[] cellBudget={0};
        for(var sheet:sheets){var parsed=rows(required(entries,sheet.path()),shared,sheet.name().equals(selected),cellBudget);if(sheet.name().equals(selected))rows=parsed;}
        return new Book(new Inspection(sheets.stream().map(Sheet::name).toList()),rows);
    }
    private static Map<String,byte[]> entries(Path file){
        var result=new LinkedHashMap<String,byte[]>();var all=new HashSet<String>();long total=0;
        try(var zip=new ZipFile(file.toFile(),StandardCharsets.UTF_8)){
            var entries=zip.entries();int count=0;
            while(entries.hasMoreElements()){
                var entry=entries.nextElement();String name=entry.getName();if(++count>2048)throw large();
                path(name.endsWith("/")?name.substring(0,name.length()-1):name);
                if(!all.add(name) || lower(name).contains("vbaproject") || lower(name).contains("encryptedpackage"))throw invalid();
                if(entry.isDirectory())continue;
                if(entry.getSize()>MAX_EXPANDED-total)throw large();
                try(var in=zip.getInputStream(entry);var bytes=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];int n;var crc=new CRC32();
                    while((n=in.read(buffer))!=-1){total+=n;if(total>MAX_EXPANDED)throw large();crc.update(buffer,0,n);bytes.write(buffer,0,n);}
                    if(entry.getCrc()!=crc.getValue() || entry.getSize()!=bytes.size())throw invalid();result.put(name,bytes.toByteArray());
                }
            }
        }catch(ZipException bad){throw invalid();}catch(IOException bad){throw invalid();}
        return result;
    }
    private record Relation(String type,String path) { }
    private static Map<String,Relation> relationships(byte[] bytes,String prefix){
        root(bytes,"Relationships",Set.of("http://schemas.openxmlformats.org/package/2006/relationships"));
        var result=new LinkedHashMap<String,Relation>();XMLStreamReader r=xml(bytes);
        try{while(r.hasNext()){if(r.next()!=XMLStreamConstants.START_ELEMENT || !r.getLocalName().equals("Relationship"))continue;
            String id=attribute(r,"Id"),type=attribute(r,"Type"),target=attribute(r,"Target");
            if(id==null || type==null || target==null || target.contains("%") || target.contains("?") || target.contains("#"))throw invalid();
            String value;
            try{var uri=URI.create(target);if(uri.isAbsolute() || uri.getAuthority()!=null || target.contains("\\"))throw invalid();value=URI.create("/"+prefix).resolve(uri).normalize().getPath();if(!value.startsWith("/"))throw invalid();value=value.substring(1);path(value);}
            catch(IllegalArgumentException bad){throw invalid();}
            if(result.put(id,new Relation(type,value))!=null)throw invalid();
        }}catch(XMLStreamException bad){throw invalid();}finally{close(r);}return result;
    }
    private static List<String> sharedStrings(byte[] bytes){
        if(bytes==null)return List.of();root(bytes,"sst",SHEET_NAMESPACES);var values=new ArrayList<String>();XMLStreamReader r=xml(bytes);StringBuilder value=null;boolean text=false;int phonetic=0;
        try{while(r.hasNext()){int event=r.next();
            if(event==XMLStreamConstants.START_ELEMENT){if(r.getLocalName().equals("si"))value=new StringBuilder();if(r.getLocalName().equals("rPh"))phonetic++;if(r.getLocalName().equals("t"))text=phonetic==0;}
            if(event==XMLStreamConstants.CHARACTERS || event==XMLStreamConstants.CDATA){if(text && value!=null)value.append(r.getText());}
            if(event==XMLStreamConstants.END_ELEMENT){if(r.getLocalName().equals("t"))text=false;if(r.getLocalName().equals("rPh"))phonetic--;if(r.getLocalName().equals("si")){if(value==null)throw invalid();values.add(value.toString());value=null;if(values.size()>1_000_000)throw large();}}
        }}catch(XMLStreamException bad){throw invalid();}finally{close(r);}return values;
    }
    private static List<Row> rows(byte[] bytes,List<String> shared,boolean collect,int[] budget){
        root(bytes,"worksheet",SHEET_NAMESPACES);
        var result=new ArrayList<Row>();XMLStreamReader r=xml(bytes);Map<Integer,String> cells=null;
        int row=0,lastRow=0,column=0,count=0;String type=null;StringBuilder value=null;boolean text=false,cell=false;
        try{while(r.hasNext()){int event=r.next();String local=event==XMLStreamConstants.START_ELEMENT || event==XMLStreamConstants.END_ELEMENT?r.getLocalName():"";
            if(event==XMLStreamConstants.START_ELEMENT){
                if(local.equals("row")){if(cells!=null)throw invalid();String index=attribute(r,"r");row=index==null?lastRow+1:integer(index);if(row<=lastRow || row>1048576)throw invalid();cells=new LinkedHashMap<>();column=0;}
                if(local.equals("c")){if(cells==null || cell || ++budget[0]>1_000_000)throw invalid();String ref=attribute(r,"r");column=ref==null?column+1:column(ref,row);if(column>16384 || cells.containsKey(column))throw invalid();type=attribute(r,"t");value=new StringBuilder();cell=true;}
                if(local.equals("v") || local.equals("t"))text=true;
            }
            if((event==XMLStreamConstants.CHARACTERS || event==XMLStreamConstants.CDATA) && text && cell)value.append(r.getText());
            if(event==XMLStreamConstants.END_ELEMENT){
                if(local.equals("v") || local.equals("t"))text=false;
                if(local.equals("c")){if(!cell)throw invalid();String content=value.toString();
                    if("s".equals(type)){int index=integer(content);if(index<0 || index>=shared.size())throw invalid();content=shared.get(index);}
                    else if("b".equals(type)){if(!Set.of("0","1").contains(content))throw invalid();}
                    else if(type!=null && !Set.of("inlineStr","str","n","d").contains(type))throw invalid();
                    formula(content);cells.put(column,content);cell=false;
                }
                if(local.equals("row")){if(cells==null || cell)throw invalid();if(cells.values().stream().anyMatch(v->!v.isBlank())){if(++count>1001)throw large();if(collect)result.add(new Row(row,cells));}lastRow=row;cells=null;}
            }
        }}catch(XMLStreamException bad){throw invalid();}finally{close(r);}
        return List.copyOf(result);
    }
    private static int column(String ref,int row){
        int n=0,i=0;while(i<ref.length() && ref.charAt(i)>='A' && ref.charAt(i)<='Z'){n=n*26+ref.charAt(i++)-'A'+1;if(n>16384)throw invalid();}
        if(n==0 || i==ref.length() || integer(ref.substring(i))!=row)throw invalid();return n;
    }
    private static int integer(String value){try{if(value==null || !value.matches("[0-9]{1,9}"))throw invalid();return Integer.parseInt(value);}catch(NumberFormatException bad){throw invalid();}}
    private static XMLStreamReader xml(byte[] bytes){try{var factory=XMLInputFactory.newFactory();factory.setProperty(XMLInputFactory.SUPPORT_DTD,false);factory.setProperty("javax.xml.stream.isSupportingExternalEntities",false);factory.setXMLResolver((p,s,b,n)->{throw new XMLStreamException("External resource prohibited");});return factory.createXMLStreamReader(new ByteArrayInputStream(bytes));}catch(XMLStreamException bad){throw invalid();}}
    private static final Set<String> SHEET_NAMESPACES=Set.of("http://schemas.openxmlformats.org/spreadsheetml/2006/main","http://purl.oclc.org/ooxml/spreadsheetml/main");
    private static void root(byte[] bytes,String local,Set<String> namespaces){
        XMLStreamReader r=xml(bytes);try{while(r.hasNext()){int event=r.next();if(event==XMLStreamConstants.DTD)throw invalid();if(event==XMLStreamConstants.START_ELEMENT){if(!r.getLocalName().equals(local) || r.getNamespaceURI()==null || !namespaces.contains(r.getNamespaceURI()))throw invalid();return;}}throw invalid();}catch(XMLStreamException bad){throw invalid();}finally{close(r);}
    }
    private static String attribute(XMLStreamReader r,String name){for(int n=0;n<r.getAttributeCount();n++)if(r.getAttributeLocalName(n).equals(name))return r.getAttributeValue(n);return null;}
    private static byte[] required(Map<String,byte[]> entries,String key){byte[] value=entries.get(key);if(value==null)throw invalid();return value;}
    private static void path(String name){if(name.isBlank() || name.startsWith("/") || name.contains("\\") || name.contains(":") || name.chars().anyMatch(Character::isISOControl) || Arrays.stream(name.split("/",-1)).anyMatch(p->p.isEmpty() || p.equals(".") || p.equals("..")))throw invalid();}
    private static void formula(String value){if(value.stripLeading().startsWith("="))throw invalid();}
    private static String lower(String value){return value==null?"":value.toLowerCase(Locale.ROOT);}
    private static void close(XMLStreamReader r){try{r.close();}catch(XMLStreamException ignored){}}
    private static IdentityFailure invalid(){return IdentityFailure.invalid();}
    private static IdentityFailure large(){return new IdentityFailure(413,"PAYLOAD_TOO_LARGE");}
    private static PersistenceFailure unavailable(){return new PersistenceFailure(PersistenceFailure.Kind.DEPENDENCY_UNAVAILABLE,null);}
}
