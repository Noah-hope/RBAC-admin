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
    @TempDir Path root;
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
