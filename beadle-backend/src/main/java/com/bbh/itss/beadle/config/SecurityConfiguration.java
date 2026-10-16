package com.bbh.itss.beadle.config;

import com.bbh.itss.beadle.adapter.in.web.SignInRecorder;
import com.bbh.itss.beadle.application.user.port.in.SignedInUserUseCase;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

/**
 * Who may call what. Two filter chains:
 * <ul>
 * <li>the services: the callbacks of Beadle-Internal and Beadle-ProTech, with the service token
 * ({@link ServiceTokenFilter}), and the health probes; stateless, without CSRF or sign-in;</li>
 * <li>the users: {@code /api/**} and the actuator need a signed-in user, the pages of the Angular app and its static
 * files are public. With {@code beadle.auth.mode=oidc} people sign in with Ping (OAuth2 login, registration
 * {@code ping}); an API call without a session is answered {@code 401}, which sends the Angular app to
 * {@value #SIGN_IN}, and every change needs the CSRF token of the {@code XSRF-TOKEN} cookie in
 * {@code X-XSRF-TOKEN}. With {@code local} everybody is {@code beadle.signed-in-user} and there is no CSRF check.
 * {@code POST /logout} ends the session in both: locally with {@code 204}, with Ping by answering where the browser
 * ends its Ping session as well ({@link PingSignOut}). {@link SignInRecorder} records the first API call of every
 * session as {@code USER_SIGNED_IN}.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties({AuthProperties.class, SignedInUserProperties.class})
public class SecurityConfiguration {

    public static final String SIGN_IN = "/oauth2/authorization/ping";
    public static final String SIGN_OUT = "/logout";
    static final String API = "/api/**";
    static final String CALLBACKS = "/api/callbacks/**";
    static final String NO_PING = "beadle.auth.mode=oidc needs the Ping registration "
            + "spring.security.oauth2.client.registration.ping: set PING_CLIENT_ID, PING_CLIENT_SECRET and "
            + "PING_ISSUER_URI, or BEADLE_AUTH_MODE=local";

    @Bean
    @Order(1)
    SecurityFilterChain serviceFilterChain(HttpSecurity http, ServicesProperties services) throws Exception {
        return http.securityMatcher(CALLBACKS, "/actuator/health", "/actuator/health/**")
                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .addFilterBefore(new ServiceTokenFilter(services), AuthorizationFilter.class)
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain userFilterChain(HttpSecurity http, AuthProperties auth, SignedInUserProperties local,
                                        SignedInUserUseCase users,
                                        ObjectProvider<ClientRegistrationRepository> registrations) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                        .requestMatchers(API, "/actuator/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(UNAUTHORIZED), apiCalls()))
                .requestCache(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(POST, SIGN_OUT)))
                .addFilterAfter(new SignInRecorder(users), AuthorizationFilter.class);
        switch (auth.mode()) {
            case LOCAL -> http.csrf(AbstractHttpConfigurer::disable)
                    .logout(logout -> logout
                            .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(NO_CONTENT)))
                    .addFilterBefore(new LocalUserFilter(local.signedInUser()), AnonymousAuthenticationFilter.class);
            case OIDC -> {
                ClientRegistrationRepository ping = registrations.getIfAvailable();
                if (ping == null) {
                    throw new IllegalStateException(NO_PING);
                }
                http.oauth2Login(Customizer.withDefaults())
                        .logout(logout -> logout.logoutSuccessHandler(new PingSignOut(ping)))
                        .csrf(csrf -> csrf.spa())
                        .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);
            }
        }
        return http.build();
    }

    static RequestMatcher apiCalls() {
        return PathPatternRequestMatcher.withDefaults().matcher(API);
    }
}
