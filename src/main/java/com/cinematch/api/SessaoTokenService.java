
package com.cinematch.api;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class SessaoTokenService {

    private static final Duration DURACAO =
            Duration.ofHours(12);

    private final SecureRandom aleatorio =
            new SecureRandom();

    private final Map<String, Sessao> sessoes =
            new ConcurrentHashMap<>();

    public String criarToken(int usuarioId) {

        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);

        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

        Instant expiracao =
                Instant.now().plus(DURACAO);

        sessoes.put(
                token,
                new Sessao(usuarioId, expiracao)
        );

        // Limpa sessões expiradas.
        Instant agora = Instant.now();

        sessoes.entrySet().removeIf(
                entrada -> !entrada.getValue()
                        .expiracao().isAfter(agora)
        );

        return token;
    }

    public Integer obterUsuarioId(
            String autorizacao
    ) {

        if (autorizacao == null
                || !autorizacao.regionMatches(
                        true, 0, "Bearer ", 0, 7
                )) {

            return null;
        }

        String token = autorizacao.substring(7).trim();

        Sessao sessao = sessoes.get(token);

        if (sessao == null) {
            return null;
        }

        if (!sessao.expiracao().isAfter(
                Instant.now()
        )) {

            sessoes.remove(token);
            return null;
        }

        return sessao.usuarioId();
    }

    private record Sessao(
            int usuarioId,
            Instant expiracao
    ) {}
}
