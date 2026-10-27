package com.adgn.importfixture;

import com.adgn.backend.imports.ImportFileStore;
import com.adgn.backend.core.transaction.PersistenceFailure;
import com.adgn.backend.identity.security.IdentityFailure;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.*;

class ImportFileStoreTest {
    @Test void publicationCollisionNeverOverwritesExistingBytesAndLeavesStageRetryable()throws Exception{
        var store=new ImportFileStore(root);var fixed=java.util.UUID.fromString("11111111-1111-4111-8111-111111111111");
        Path existing=root.resolve(fixed+".blob");Files.writeString(existing,"previous-upload");
        try(var stage=store.stage(new ByteArrayInputStream("new-upload".getBytes(StandardCharsets.UTF_8)))){
            try(var random=org.mockito.Mockito.mockStatic(java.util.UUID.class,org.mockito.Mockito.CALLS_REAL_METHODS)){
                random.when(java.util.UUID::randomUUID).thenReturn(fixed);
                assertThatThrownBy(()->store.publish(stage)).isInstanceOfSatisfying(PersistenceFailure.class,
                        e->assertThat(e.kind()).isEqualTo(PersistenceFailure.Kind.DEPENDENCY_UNAVAILABLE));
            }
            assertThat(Files.readString(existing)).isEqualTo("previous-upload");
            assertThat(Files.readString(stage.path())).isEqualTo("new-upload");
            var published=store.publish(stage);
            assertThat(Files.readString(store.verifiedPath(published.storageRef(),published.sizeBytes(),published.sha256()))).isEqualTo("new-upload");
        }
    }
    @TempDir Path root;
    @Test void oversizedStreamIsRejectedAndItsPartialStageIsRemoved()throws Exception {
        var store=new ImportFileStore(root);
        byte[] oversized=new byte[com.adgn.backend.imports.ImportFileParser.MAX_FILE+1];
        assertThatThrownBy(()->store.stage(new ByteArrayInputStream(oversized)))
                .isInstanceOfSatisfying(IdentityFailure.class,e->assertThat(e.status()).isEqualTo(413));
        try(var files=Files.list(root)){assertThat(files.count()).isZero();}
    }
    @Test void stageBelongsToItsStoreAndTamperingPreventsPublication()throws Exception {
        var store=new ImportFileStore(root);var other=new ImportFileStore(root.resolve("other"));
        assertThatThrownBy(()->store.publish(null)).isInstanceOf(IllegalStateException.class);
        try(var stage=store.stage(new ByteArrayInputStream("a,b".getBytes(StandardCharsets.UTF_8)))){
            assertThatThrownBy(()->other.publish(stage)).isInstanceOf(IllegalStateException.class);
            Files.writeString(stage.path(),"changed size");
            assertThatThrownBy(()->store.publish(stage)).isInstanceOfSatisfying(PersistenceFailure.class,
                    e->assertThat(e.kind()).isEqualTo(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR));
        }
        try(var files=Files.list(root)){assertThat(files.filter(p->p.toString().endsWith(".blob")).count()).isZero();}
    }
    @Test void metadataBoundsAndNonRegularBlobCannotBypassIntegrityChecks()throws Exception {
        var store=new ImportFileStore(root);String reference="123e4567-e89b-12d3-a456-426614174000.blob";
        for(long size:new long[]{0,com.adgn.backend.imports.ImportFileParser.MAX_FILE+1L})
            assertThatThrownBy(()->store.verifiedPath(reference,size,"a".repeat(64))).isInstanceOf(PersistenceFailure.class);
        for(String hash:new String[]{null,"bad","A".repeat(64)})
            assertThatThrownBy(()->store.verifiedPath(reference,1,hash)).isInstanceOf(PersistenceFailure.class);
        assertThatThrownBy(()->store.verifiedPath(null,1,"a".repeat(64))).isInstanceOf(PersistenceFailure.class);
        Files.createDirectory(root.resolve(reference));
        assertThatThrownBy(()->store.verifiedPath(reference,1,"a".repeat(64)))
                .isInstanceOfSatisfying(PersistenceFailure.class,e->assertThat(e.kind()).isEqualTo(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR));
    }
    @Test void anExistingRegularFileCannotBecomeTheStorageRoot()throws Exception {
        Path file=root.resolve("file");Files.writeString(file,"existing");
        assertThatThrownBy(()->new ImportFileStore(file)).isInstanceOfSatisfying(PersistenceFailure.class,
                e->assertThat(e.kind()).isEqualTo(PersistenceFailure.Kind.DEPENDENCY_UNAVAILABLE));
        assertThat(Files.readString(file)).isEqualTo("existing");
    }
    @Test void durableBytesSurviveReopeningAndNoClientFileNameBecomesAPath()throws Exception{
        var store=new ImportFileStore(root);byte[] bytes="key,name\n007,Person\n".getBytes(StandardCharsets.UTF_8);
        ImportFileStore.Stored stored;
        try(var stage=store.stage(new ByteArrayInputStream(bytes))){stored=store.publish(stage);assertThat(stored.sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));}
        var reopened=new ImportFileStore(root);assertThat(Files.readAllBytes(reopened.verifiedPath(stored.storageRef(),stored.sizeBytes(),stored.sha256()))).isEqualTo(bytes);
        assertThat(stored.storageRef()).matches("[a-f0-9-]{36}\\.blob");
        try(var names=Files.list(root)){assertThat(names.map(p->p.getFileName().toString()).toList()).containsExactly(stored.storageRef());}
    }
    @Test void stagingFailureAndEmptyInputLeaveNoPublishedFiles()throws Exception{
        var store=new ImportFileStore(root);
        assertThatThrownBy(()->store.stage(new InputStream(){public int read()throws IOException{throw new IOException("fixture failure");}})).isInstanceOf(PersistenceFailure.class);
        assertThatThrownBy(()->store.stage(new ByteArrayInputStream(new byte[0]))).isInstanceOf(IdentityFailure.class);
        try(var names=Files.list(root)){assertThat(names.count()).isZero();}
    }
    @Test void detectsMissingCorruptAndInvalidReferencesWithoutReturningOutsidePaths()throws Exception{
        var store=new ImportFileStore(root);ImportFileStore.Stored stored;
        try(var stage=store.stage(new ByteArrayInputStream("a,b\nx,y\n".getBytes(StandardCharsets.UTF_8)))){stored=store.publish(stage);}
        Path path=store.verifiedPath(stored.storageRef(),stored.sizeBytes(),stored.sha256());Files.writeString(path,"a,b\nx,z\n");
        assertThatThrownBy(()->store.verifiedPath(stored.storageRef(),stored.sizeBytes(),stored.sha256())).isInstanceOfSatisfying(PersistenceFailure.class,e->assertThat(e.kind()).isEqualTo(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR));
        Files.delete(path);assertThatThrownBy(()->store.verifiedPath(stored.storageRef(),stored.sizeBytes(),stored.sha256())).isInstanceOfSatisfying(PersistenceFailure.class,e->assertThat(e.kind()).isEqualTo(PersistenceFailure.Kind.DEPENDENCY_UNAVAILABLE));
        for(String ref:new String[]{"../outside","C:/outside","/outside","random.blob"})assertThatThrownBy(()->store.verifiedPath(ref,1,"a".repeat(64))).isInstanceOfSatisfying(PersistenceFailure.class,e->assertThat(e.kind()).isEqualTo(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR));
    }
    @Test void abandonedOrAlreadyPublishedStageCannotBeRepublished()throws Exception{
        var store=new ImportFileStore(root);
        try(var stage=store.stage(new ByteArrayInputStream("a,b".getBytes(StandardCharsets.UTF_8)))){store.publish(stage);assertThatThrownBy(()->store.publish(stage)).isInstanceOf(IllegalStateException.class);}
        var closed=store.stage(new ByteArrayInputStream("a,b".getBytes(StandardCharsets.UTF_8)));closed.close();assertThatThrownBy(()->store.publish(closed)).isInstanceOf(IllegalStateException.class);
    }
}
