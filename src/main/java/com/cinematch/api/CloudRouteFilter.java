
package com.cinematch.api;

import java.io.IOException;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Profile("cloud")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CloudRouteFilter
        extends OncePerRequestFilter {

    // Rotas GET permitidas na nuvem.
    private static final Set<String> ROTAS_GET =
            Set.of(
                    "/api/saude",
                    "/api/filmes",
                    "/api/filmes/buscar",
                    "/api/preferencias/curtidos",
                    "/api/preferencias/salvos",
                    "/api/matches/conexoes"
            );

    // Rotas POST permitidas na nuvem.
    private static final Set<String> ROTAS_POST =
            Set.of(
                    "/api/login",
                    "/api/cadastro",
                    "/api/preferencias/curtir",
                    "/api/preferencias/salvar",
                    "/api/matches/solicitar",
                    "/api/mensagens/enviar",
                    "/api/comentarios"
            );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        String metodo = request.getMethod();
        String caminho = request.getServletPath();

        boolean permitido = false;

        // =====================================
        // CONSULTAS GET
        // =====================================

        if ("GET".equals(metodo)) {

            permitido = ROTAS_GET.contains(caminho);

            // Quantidade de curtidas de um filme.
            if (caminho.matches(
                    "/api/preferencias/filmes/[0-9]+/curtidas"
            )) {
                permitido = true;
            }

            // Matches de um filme.
            if (caminho.matches(
                    "/api/matches/filmes/[0-9]+"
            )) {
                permitido = true;
            }

            // Status entre duas contas.
            if (caminho.matches(
                    "/api/matches/status/[0-9]+"
            )) {
                permitido = true;
            }

            // Historico de mensagens.
            if (caminho.matches(
                    "/api/mensagens/conversa/[0-9]+"
            )) {
                permitido = true;
            }

            // Listar comentarios de um filme.
            if (caminho.matches(
                    "/api/comentarios/filmes/[0-9]+"
            )) {
                permitido = true;
            }
        }

        // =====================================
        // OPERACOES POST
        // =====================================

        if ("POST".equals(metodo)) {

            permitido = ROTAS_POST.contains(caminho);

            // Aceitar ou recusar uma conexao.
            if (caminho.matches(
                    "/api/matches/conexoes/[0-9]+/responder"
            )) {
                permitido = true;
            }

            // Curtir um comentario.
            if (caminho.matches(
                    "/api/comentarios/[0-9]+/curtir"
            )) {
                permitido = true;
            }
        }

        // =====================================
        // OPERACOES DELETE
        // =====================================

        if ("DELETE".equals(metodo)) {

            // Apagar comentarios do usuario
            // em um filme especifico.
            if (caminho.matches(
                    "/api/comentarios/filmes/[0-9]+/meus"
            )) {
                permitido = true;
            }
        }

        // =====================================
        // BLOQUEAR ROTAS NAO AUTORIZADAS
        // =====================================

        if (!permitido) {

            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Rota indisponivel na versao de teste."
            );

            return;
        }

        // Cada controller verifica o token
        // e as permissoes do usuario.
        chain.doFilter(request, response);
    }
}
