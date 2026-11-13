
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
                    "/api/matches/solicitar"
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

        if ("GET".equals(metodo)) {

            permitido = ROTAS_GET.contains(caminho);

            // Quantidade publica de curtidas.
            if (caminho.matches(
                    "/api/preferencias/filmes/[0-9]+/curtidas"
            )) {
                permitido = true;
            }

            // Buscar matches de um filme.
            if (caminho.matches(
                    "/api/matches/filmes/[0-9]+"
            )) {
                permitido = true;
            }

            // Consultar status entre duas contas.
            if (caminho.matches(
                    "/api/matches/status/[0-9]+"
            )) {
                permitido = true;
            }
        }

        if ("POST".equals(metodo)) {

            permitido = ROTAS_POST.contains(caminho);

            // Aceitar ou recusar uma conexao.
            if (caminho.matches(
                    "/api/matches/conexoes/[0-9]+/responder"
            )) {
                permitido = true;
            }
        }

        if (!permitido) {

            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Rota indisponivel na versao de teste."
            );

            return;
        }

        // Continua para o controlador correspondente.
        // Os endpoints de matches continuam exigindo
        // o token de autenticacao do usuario.
        chain.doFilter(request, response);
    }
}
