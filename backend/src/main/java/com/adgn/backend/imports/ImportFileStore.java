package com.adgn.backend.imports;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.security.*;
import java.util.HexFormat;
import java.util.UUID;
import com.adgn.backend.core.transaction.PersistenceFailure;
import com.adgn.backend.identity.security.IdentityFailure;

/** Private, immutable blobs. Storage refs are generated here and never accepted as client paths. */
public final class ImportFileStore {
    public record Stored(UUID id,String storageRef,long sizeBytes,String sha256) { }
    public static final class Stage implements AutoCloseable {
        private final ImportFileStore owner;private final Path path;private final long size;private final String hash;
        private boolean published,closed;
        private Stage(ImportFileStore owner,Path path,long size,String hash){this.owner=owner;this.path=path;this.size=size;this.hash=hash;}
        public Path path(){return path;}
        public long sizeBytes(){return size;}
        public String sha256(){return hash;}
        public void close(){if(closed)return;try{Files.deleteIfExists(path);closed=true;}catch(IOException bad){throw unavailable();}}
    }
    private final Path root;
    public ImportFileStore(Path root){
        try{Path absolute=root.toAbsolutePath().normalize();if(Files.isSymbolicLink(absolute))throw unavailable();Files.createDirectories(absolute);this.root=absolute.toRealPath();
            if(!Files.isDirectory(this.root) || !Files.isWritable(this.root))throw unavailable();
        }catch(IOException bad){throw unavailable();}
    }
    public Stage stage(InputStream input){
        Path path=null;
        try(var in=input){
            path=Files.createTempFile(root,"stage-",".part");MessageDigest digest=digest();long size=0;
            try(var channel=FileChannel.open(path,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1){size+=n;if(size>ImportFileParser.MAX_FILE)throw new IdentityFailure(413,"PAYLOAD_TOO_LARGE");digest.update(buffer,0,n);var bytes=ByteBuffer.wrap(buffer,0,n);while(bytes.hasRemaining())channel.write(bytes);}
                if(size==0)throw IdentityFailure.invalid();channel.force(true);
            }
            return new Stage(this,path,size,HexFormat.of().formatHex(digest.digest()));
        }catch(IOException bad){cleanup(path);throw unavailable();}catch(RuntimeException bad){cleanup(path);throw bad;}
    }
    public Stored publish(Stage stage){
        if(stage==null || stage.owner!=this || stage.closed || stage.published)throw new IllegalStateException("Stage is not publishable");
        check(stage.path,stage.size,stage.hash);UUID id=UUID.randomUUID();String ref=id+".blob";
        try{
            // Link creation is atomic, requires a new target, and never replaces existing bytes.
            Files.createLink(root.resolve(ref),stage.path);forceDirectory();stage.published=true;
            return new Stored(id,ref,stage.size,stage.hash);
        }catch(IOException bad){throw unavailable();}
    }
    public Path verifiedPath(String reference,long size,String hash){
        if(reference==null || !reference.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.blob") || size<1 || size>ImportFileParser.MAX_FILE || hash==null || !hash.matches("[a-f0-9]{64}"))throw integrity();
        Path path=root.resolve(reference);check(path,size,hash);return path;
    }
    private static void check(Path path,long size,String hash){
        try{
            var attributes=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.size()!=size)throw integrity();
            MessageDigest digest=digest();long seen=0;
            try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){seen+=n;if(seen>size)throw integrity();digest.update(buffer,0,n);}}
            if(seen!=size || !MessageDigest.isEqual(digest.digest(),HexFormat.of().parseHex(hash)))throw integrity();
        }catch(IOException bad){throw unavailable();}
    }
    private void forceDirectory()throws IOException{if(Files.getFileStore(root).supportsFileAttributeView("posix"))try(var directory=FileChannel.open(root,StandardOpenOption.READ)){directory.force(true);}}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 unavailable");}}
    private static void cleanup(Path path){if(path!=null)try{Files.deleteIfExists(path);}catch(IOException ignored){}}
    private static PersistenceFailure unavailable(){return new PersistenceFailure(PersistenceFailure.Kind.DEPENDENCY_UNAVAILABLE,null);}
    private static PersistenceFailure integrity(){return new PersistenceFailure(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR,null);}
}
