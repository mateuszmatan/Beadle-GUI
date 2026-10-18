package com.bbh.itss.beadle.config;

import com.bbh.itss.beadle.adapter.in.web.CsrfCookieFilter;
import com.bbh.itss.beadle.adapter.in.web.LocalUserFilter;
import com.bbh.itss.beadle.adapter.in.web.PingSignOut;
import com.bbh.itss.beadle.adapter.in.web.RefusedRequests;
import com.bbh.itss.beadle.adapter.in.web.ServiceTokenFilter;
import com.bbh.itss.beadle.adapter.in.web.SignInRecorder;
import com.bbh.itss.beadle.adapter.out.http.ServicesProperties;
import com.bbh.itss.beadle.application.user.port.in.SignedInUserUseCase;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutFilter;
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
 * {@code X-XSRF-TOKEN}. With {@code local}, which the Oracle profiles refuse, everybody is {@code beadle.auth.user}
 * and there is no CSRF check.
 * {@code POST /logout} ends the session in both: locally with {@code 204}, with Ping by answering where the browser
 * ends its Ping session as well ({@link PingSignOut}).</li>
 * </ul>
 * The audit trail gets the sessions and what these chains refuse: {@link SignInRecorder} records the first API call of
 * every session as {@code USER_SIGNED_IN}, the sign-out of a signed-in user is {@code USER_SIGNED_OUT} (a
 * {@code POST /logout} after the session ended is not), and {@link RefusedRequests} files a signed-in user's change
 * refused for its CSRF token and a callback with the service token refused for its correlation id as {@code FAILURE}
 * rows; a caller who proved nothing, or whose Ping sign-in failed, is only logged.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfiguration {

    public static final String SIGN_IN = "/oauth2/authorization/ping";
    public static final String SIGN_OUT = "/logout";
    /** Where a failed sign-in ends: Spring's sign-in page, which says that it failed. */
    static final String SIGN_IN_FAILED = "/login?error";
    static final String API = "/api/**";
    static final String NO_PING = "beadle.auth.mode=oidc needs the Ping registration "
            + "spring.security.oauth2.client.registration.ping: set PING_CLIENT_ID, PING_CLIENT_SECRET and "
            + "PING_ISSUER_URI";
    static final String LOCAL_ONLY = "beadle.auth.mode=local signs everybody in without Ping: it is for profile local "
            + "only, not for rd, qc or prod";

    private static final AuthenticationTrustResolver TRUST = new AuthenticationTrustResolverImpl();

    @Bean
    @Order(1)
    SecurityFilterChain serviceFilterChain(HttpSecurity http, ServicesProperties services, RefusedRequests refusals)
            throws Exception {
        return http.securityMatcher(ServiceTokenFilter.CALLBACKS, "/actuator/health", "/actuator/health/**")
                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .addFilterBefore(new ServiceTokenFilter(services.token(), refusals), AuthorizationFilter.class)
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain userFilterChain(HttpSecurity http, AuthProperties auth, Environment environment,
                                        SignedInUserUseCase users, RefusedRequests refusals,
                                        ObjectProvider<ClientRegistrationRepository> registrations) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                        .requestMatchers(API, "/actuator/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(UNAUTHORIZED), apiCalls())
                        .accessDeniedHandler(refusals.forbidden()))
                .requestCache(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(POST, SIGN_OUT))
                        .addLogoutHandler((request, response, authentication) -> {
                            if (TRUST.isAuthenticated(authentication)) {
                                users.signOut();
                            }
                        }))
                .addFilterAfter(new SignInRecorder(users), AuthorizationFilter.class);
        switch (auth.mode()) {
            case LOCAL -> {
                if (environment.matchesProfiles("oracle")) {
                    throw new IllegalStateException(LOCAL_ONLY);
                }
                http.csrf(AbstractHttpConfigurer::disable)
                        .logout(logout -> logout
                                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(NO_CONTENT)))
                        .addFilterBefore(new LocalUserFilter(auth.user()), LogoutFilter.class);
            }
            case OIDC -> {
                ClientRegistrationRepository ping = registrations.getIfAvailable();
                if (ping == null) {
                    throw new IllegalStateException(NO_PING);
                }
                http.oauth2Login(login -> login.withObjectPostProcessor(failuresRecordedBy(refusals)))
                        .logout(logout -> logout.logoutSuccessHandler(new PingSignOut(ping)))
                        .csrf(csrf -> csrf.spa())
                        .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);
            }
        }
        return http.build();
    }

    /**
     * Files a failed Ping sign-in before the sign-in page says it failed. Set on the filter rather than as the failure
     * handler of the login, so that the generated sign-in page still knows {@value #SIGN_IN_FAILED} as its error page.
     */
    private static ObjectPostProcessor<OAuth2LoginAuthenticationFilter> failuresRecordedBy(RefusedRequests refusals) {
        return new ObjectPostProcessor<OAuth2LoginAuthenticationFilter>() {
            @Override
            public <O extends OAuth2LoginAuthenticationFilter> O postProcess(O filter) {
                filter.setAuthenticationFailureHandler(
                        refusals.signInFailures(new SimpleUrlAuthenticationFailureHandler(SIGN_IN_FAILED)));
                return filter;
            }
        };
    }

    static RequestMatcher apiCalls() {
        return PathPatternRequestMatcher.withDefaults().matcher(API);
    }
}
