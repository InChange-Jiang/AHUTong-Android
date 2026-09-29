import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Signature;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/**
 * .ahup 签名器（单文件 Java，JDK 11+ 直接 `java AhupSign.java ...` 跑）。
 * 用法：java AhupSign.java <keystore.jks> <storepass> <alias> <keypass> <plugin.dex> <icon.png> [so...]
 * 输出：签名（Base64）到 stdout 最后一行。
 *
 * 签名载荷（与宿主 AhupInstaller.verifySignature 两端一致）：
 *   v1（无 .so 参数）：  SHA256withRSA over sha256(dex + icon)
 *   v2（有 .so 参数）：  SHA256withRSA over sha256(dex + icon + 全部 .so 按参数顺序拼接)
 * .so 须按 zip 内相对路径的排序结果依次传入（package_ahup.py 已保证）。
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

        List<byte[]> parts = new ArrayList<>();
        parts.add(dex);
        parts.add(icon);
        for (int i = 6; i < args.length; i++) {
            parts.add(Files.readAllBytes(Paths.get(args[i])));
        }
        byte[] payload = concatAll(parts);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload);

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(key);
        sig.update(digest);
        System.out.println(Base64.getEncoder().encodeToString(sig.sign()));
    }

    private static byte[] concatAll(List<byte[]> parts) {
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] out = new byte[total];
        int offset = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, offset, p.length);
            offset += p.length;
        }
        return out;
    }
}
