package top.ceclin.pslock.security;

public final class KeyProvider {

    static {
        System.loadLibrary("key");
    }

    private KeyProvider() {
    }

    public static final byte[] AES_KEY = aesKey();

    private static native byte[] aesKey();

}
