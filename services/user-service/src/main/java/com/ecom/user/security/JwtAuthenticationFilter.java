package com.ecom.user.security;

import com.ecom.user.domain.Role;
import com.ecom.user.repository.UserRepository;
import com.ecom.user.repository.UserRoleRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Validates a JWT on every protected request and populates the SecurityContext.
 *
 * <p>Flow:</p>
 * <ol>
 *   <li>Extract {@code Authorization: Bearer &lt;token&gt;} header.</li>
 *   <li>Parse + verify via {@link JwtService}. Any failure ⇒ leave context empty
 *       and let downstream filters return 401.</li>
 *   <li>Build an {@link AppUserPrincipal} (without DB hit when possible) from claims.</li>
 *   <li>Set {@link SecurityContextHolder}.</li>
 * </ol>
 *
 * <p>We <em>do</em> hit the DB once per request to ensure the user is still active
 * (not suspended, not deleted). For high-traffic endpoints this can be replaced by
 * a JWT claim like {@code "ver"} + a small cache, but that's a future optimization.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService         jwtService;
    private final UserRepository     userRepository;
    private final UserRoleRepository userRoleRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserRepository userRepository,
                                   UserRoleRepository userRoleRepository) {
        this.jwtService         = jwtService;
        this.userRepository     = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();

        try {
            Claims      claims    = jwtService.parseAndValidate(token);
            UUID        publicId  = JwtService.extractSubject(claims);
            Set<Role>   jwtRoles  = JwtService.extractRoles(claims);

            // DB check: user still exists + active. (Avoid trusting JWT after revoke.)
            var user = userRepository.findByPublicId(publicId).orElse(null);
            if (user == null || !user.isActive()) {
                log.debug("token rejected: user not active for publicId={}", publicId);
                chain.doFilter(request, response);
                return;
            }

            var principal = new AppUserPrincipal(user, jwtRoles);
            var auth = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("authenticated request as {}", principal.email());

        } catch (JwtException ex) {
            // Bad signature / expired / wrong issuer / malformed — leave context empty.
            // ExceptionTranslationFilter ⇒ AuthenticationEntryPoint ⇒ 401.
            log.debug("jwt rejected: {}", ex.getMessage());
        } catch (Exception ex) {
            log.warn("unexpected error parsing jwt: {}", ex.getMessage());
        }

        chain.doFilter(request, response);
    }
}
