package com.rapmonet.securite.internal;

import com.rapmonet.securite.Habilitations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;

import static com.rapmonet.securite.Habilitations.menu;

/**
 * Sécurité HTTP : session côté serveur (cookie HttpOnly) et protection CSRF par cookie
 * XSRF-TOKEN lu par Angular. Chaque API est réservée au menu correspondant du profil.
 */
@Configuration
class ConfigurationSecurite {

    @Bean
    PasswordEncoder encodeurMotDePasse() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityContextRepository depotContexteSecurite() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain filtres(HttpSecurity http, AuthentificationService authentification,
                                SecurityContextRepository depot) throws Exception {
        String u = Habilitations.UTILISATEUR;
        http
                .csrf(csrf -> csrf.spa())
                .securityContext(c -> c.securityContextRepository(depot))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterAfter(new FiltreRafraichissementSession(authentification), SecurityContextHolderFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/auth/connexion").permitAll()
                        .requestMatchers("/api/auth/**").authenticated()
                        .requestMatchers("/api/securite/utilisateurs/**").hasAuthority(menu("SECURITE_UTILISATEURS"))
                        .requestMatchers(HttpMethod.GET, "/api/securite/profils", "/api/securite/profils/**")
                        .hasAnyAuthority(menu("SECURITE_PROFILS"), menu("SECURITE_UTILISATEURS"))
                        .requestMatchers("/api/securite/profils/**", "/api/securite/menus").hasAuthority(menu("SECURITE_PROFILS"))
                        .requestMatchers("/api/securite/audit/**").hasAuthority(menu("SECURITE_AUDIT"))
                        .requestMatchers("/api/ingestion/lis/**").hasAuthority(menu("CHARGEMENT_LIS"))
                        .requestMatchers("/api/exploration/lis/**").hasAuthority(menu("EXPLORATION_LIS"))
                        .requestMatchers("/api/ingestion/vss/**").hasAuthority(menu("CHARGEMENT_VSS"))
                        .requestMatchers("/api/exploration/vss/**").hasAuthority(menu("EXPLORATION_VSS"))
                        .requestMatchers("/api/ingestion/ejournal/**").hasAuthority(menu("CHARGEMENT_EJOURNAL"))
                        .requestMatchers("/api/exploration/ejournal/**").hasAuthority(menu("EXPLORATION_EJOURNAL"))
                        .requestMatchers(HttpMethod.GET, "/api/parametrage/**").hasAuthority(u)
                        .requestMatchers("/api/parametrage/vss/**").hasAuthority(menu("PARAMETRAGE_VSS"))
                        .requestMatchers("/api/parametrage/**").hasAuthority(menu("PARAMETRAGE_LIS"))
                        .requestMatchers("/api/systeme/**").hasAuthority(u)
                        .requestMatchers("/api/**").denyAll()
                        .anyRequest().permitAll());
        return http.build();
    }
}
