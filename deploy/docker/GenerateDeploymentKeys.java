import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.security.interfaces.*;
import java.util.*;

/** Standalone Java 21 source launcher. No Maven, network, database or application startup. */
public final class GenerateDeploymentKeys {
    private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    public static void main(String[] args) {
        try {
            if (args.length != 1) throw new IllegalArgumentException();
            Path directory = Path.of(args[0]).normalize();
            if (!directory.isAbsolute() || Files.isSymbolicLink(directory)) throw new IllegalArgumentException();
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException();
                try (var entries = Files.list(directory)) {
                    if (entries.findAny().isPresent()) throw new FileAlreadyExistsException("Nonempty key directory");
                }
            }
            // Generate all material before touching output files. Never read or print private material.
            var documents = new LinkedHashMap<String, String>();
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            for (String family : List.of("admin", "business", "oa")) {
                var pair = generator.generateKeyPair();
                var publicKey = (RSAPublicKey) pair.getPublic();
                var privateKey = (RSAPrivateCrtKey) pair.getPrivate();
                String prefix = "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + family
                        + "-v1\",\"use\":\"sig\",\"alg\":\"RS256\",\"n\":\""
                        + encoded(publicKey.getModulus()) + "\",\"e\":\""
                        + encoded(publicKey.getPublicExponent()) + "\"";
                documents.put(family + "-public.json", prefix + "}]}\n");
                documents.put(family + "-private.json", prefix
                        + ",\"d\":\"" + encoded(privateKey.getPrivateExponent())
                        + "\",\"p\":\"" + encoded(privateKey.getPrimeP())
                        + "\",\"q\":\"" + encoded(privateKey.getPrimeQ())
                        + "\",\"dp\":\"" + encoded(privateKey.getPrimeExponentP())
                        + "\",\"dq\":\"" + encoded(privateKey.getPrimeExponentQ())
                        + "\",\"qi\":\"" + encoded(privateKey.getCrtCoefficient()) + "\"}]}\n");
            }
            byte[] hmac = new byte[32];
            new SecureRandom().nextBytes(hmac);
            documents.put("idempotency-keys.json", "{\"server-v1\":\""
                    + Base64.getEncoder().encodeToString(hmac) + "\"}\n");
            Files.createDirectories(directory, permissions("rwx------"));
            if (POSIX) Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
            for (var document : documents.entrySet()) {
                try (var file = Files.newByteChannel(directory.resolve(document.getKey()),
                        Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), permissions("rw-------"))) {
                    var buffer = ByteBuffer.wrap(document.getValue().getBytes(StandardCharsets.UTF_8));
                    while (buffer.hasRemaining()) file.write(buffer);
                }
            }
            System.out.println("KEY_SETUP_CREATED: admin-v1, business-v1, oa-v1 and server-v1; preserve these files.");
        } catch (Exception failure) {
            // Do not include exception messages, key objects or file contents.
            System.err.println("KEY_SETUP_REFUSED: " + failure.getClass().getSimpleName()
                    + "; use an empty absolute directory, existing files are never overwritten.");
            System.exit(2);
        }
    }

    private static String encoded(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static FileAttribute<?>[] permissions(String value) {
        return POSIX ? new FileAttribute<?>[] {
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(value))
        } : new FileAttribute<?>[0];
    }
}
