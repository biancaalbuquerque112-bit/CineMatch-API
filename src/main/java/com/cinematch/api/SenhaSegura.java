
package com.cinematch.api;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class SenhaSegura {

    private SenhaSegura() {}

    private static final int ITERACOES = 120000;

    public static String criar(String senha) {
        try {
            byte[] sal = new byte[16];
            new SecureRandom().nextBytes(sal);

            return "pbkdf2$" + ITERACOES + "$"
                + Base64.getEncoder().encodeToString(sal) + "$"
                + Base64.getEncoder().encodeToString(
                    derivar(senha, sal, ITERACOES)
                );
        } catch (Exception e) {
            throw new IllegalStateException(
                "Erro ao proteger senha", e
            );
        }
    }

    private static byte[] derivar(
        String senha, byte[] sal, int iteracoes
    ) throws Exception {

        PBEKeySpec spec = new PBEKeySpec(
            senha.toCharArray(), sal, iteracoes, 256
        );

        try {
            return SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    public static boolean verificar(
        String senha, String armazenada
    ) {
        if (senha == null || armazenada == null) {
            return false;
        }

        if (!armazenada.startsWith("pbkdf2$")) {
            // Compatibilidade temporária com contas antigas.
            return MessageDigest.isEqual(
                senha.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                armazenada.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
        }

        try {
            String[] partes = armazenada.split("\\$");

            if (partes.length != 4) {
                return false;
            }

            return MessageDigest.isEqual(
                derivar(
                    senha,
                    Base64.getDecoder().decode(partes[2]),
                    Integer.parseInt(partes[1])
                ),
                Base64.getDecoder().decode(partes[3])
            );
        } catch (Exception e) {
            return false;
        }
    }
}
