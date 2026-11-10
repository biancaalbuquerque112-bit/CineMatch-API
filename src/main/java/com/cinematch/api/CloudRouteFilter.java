
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

    // Rotas de leitura permitidas na nuvem.
    private static final Set<String> ROTAS_GET =
            Set.of(
                    "/api/saude",
                    "/api/filmes",
                    "/api/filmes/buscar",
                    "/api/preferencias/curtidos",
                    "/api/preferencias/salvos"
            );

    // Rotas de escrita permitidas na nuvem.
    private static final Set<String> ROTAS_POST =
            Set.of(
                    "/api/login",
                    "/api/cadastro",
                    "/api/preferencias/curtir",
                    "/api/preferencias/salvar"
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

        // Consultas permitidas
        if ("GET".equals(metodo)) {

            permitido = ROTAS_GET.contains(caminho);

            // Consulta publica da quantidade de curtidas.
            if (caminho.matches(
                    "/api/preferencias/filmes/[0-9]+/curtidas"
            )) {
                permitido = true;
            }
        }

        // Operacoes permitidas
        if ("POST".equals(metodo)) {
            permitido = ROTAS_POST.contains(caminho);
        }

        if (!permitido) {

            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Rota indisponivel na versao de teste."
            );

            return;
        }

        chain.doFilter(request, response);
    }
}
