package bo.utb.ajedrez.comun;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** PIN de vinculación y tokens de los celulares. En la base solo se guarda su hash SHA-256. */
public final class Secretos {
    private Secretos() {}

    private static final SecureRandom AZAR = new SecureRandom();

    public static String pin() {
        return String.format("%06d", AZAR.nextInt(1_000_000));
    }

    public static String token() {
        byte[] b = new byte[32];
        AZAR.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String hash(String secreto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secreto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
