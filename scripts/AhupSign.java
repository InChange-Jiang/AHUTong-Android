import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Signature;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * .ahup 签名器（单文件 Java，JDK 11+ 直接 `java AhupSign.java ...` 跑）。
 * 用法：java AhupSign.java <keystore.jks> <storepass> <alias> <keypass> <plugin.dex> <icon.png>
 * 输出：签名（Base64，SHA256withRSA over sha256(dex+icon)）到 stdout 最后一行。
 */
public class AhupSign {
    public static void main(String[] args) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream in = new FileInputStream(args[0])) {
            ks.load(in, args[1].toCharArray());
        }
        java.security.PrivateKey key = (java.security.PrivateKey) ks.getKey(args[2], args[3].toCharArray());

        byte[] dex = Files.readAllBytes(Paths.get(args[4]));
        byte[] icon = Files.readAllBytes(Paths.get(args[5]));
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(concat(dex, icon));

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(key);
        sig.update(digest);
        System.out.println(Base64.getEncoder().encodeToString(sig.sign()));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
