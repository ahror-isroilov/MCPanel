package mc.server.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class SetupFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        if (InitialSetupRunner.isSetupRequired() && !isAllowedPath(req)) {
            res.sendRedirect("/setup");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isAllowedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/setup") || path.startsWith("/css/") || path.startsWith("/js/") || path.equals("/favicon.ico");
    }
}
