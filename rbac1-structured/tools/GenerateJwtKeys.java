import java.nio.file.*;
import java.security.*;
import java.util.Base64;

public class GenerateJwtKeys {
    public static void main(String[] args) throws Exception {
        var directory = Path.of(args.length == 0 ? ".local" : args[0]);
        Files.createDirectories(directory);
        var privatePath = directory.resolve("jwt-private.pem");
        var publicPath = directory.resolve("jwt-public.pem");
        if (Files.exists(privatePath) || Files.exists(publicPath)) {
            throw new IllegalStateException("Key files already exist; choose an empty directory.");
        }
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        var encoder = Base64.getMimeEncoder(64, new byte[]{10});
        Files.writeString(privatePath, "-----BEGIN PRIVATE KEY-----\n" + encoder.encodeToString(keys.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n", StandardOpenOption.CREATE_NEW);
        Files.writeString(publicPath, "-----BEGIN PUBLIC KEY-----\n" + encoder.encodeToString(keys.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----\n", StandardOpenOption.CREATE_NEW);
        System.out.println("Keys saved in " + directory.toAbsolutePath());
    }
}
