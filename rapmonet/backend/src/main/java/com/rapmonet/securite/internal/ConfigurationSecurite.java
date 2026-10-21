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
                        .requestMatchers(HttpMethod.POST, "/api/gab/*/*/validation", "/api/gab/*/*/annulation")
                        .hasAuthority(menu("GAB_VALIDATION"))
                        .requestMatchers(HttpMethod.POST, "/api/gab/appros").hasAuthority(menu("GAB_APPRO"))
                        .requestMatchers(HttpMethod.PUT, "/api/gab/appros/*").hasAuthority(menu("GAB_APPRO"))
                        .requestMatchers(HttpMethod.POST, "/api/gab/arretes").hasAuthority(menu("GAB_ARRETE"))
                        .requestMatchers(HttpMethod.PUT, "/api/gab/arretes/*").hasAuthority(menu("GAB_ARRETE"))
                        .requestMatchers(HttpMethod.GET, "/api/gab/appros", "/api/gab/appros/**")
                        .hasAnyAuthority(menu("GAB_APPRO"), menu("GAB_ARRETE"), menu("GAB_VALIDATION"))
                        .requestMatchers(HttpMethod.GET, "/api/gab/arretes", "/api/gab/arretes/**")
                        .hasAnyAuthority(menu("GAB_ARRETE"), menu("GAB_VALIDATION"))
                        .requestMatchers("/api/chargebacks/emission", "/api/chargebacks/emission/**")
                        .hasAuthority(menu("CHARGEBACK_EMISSION"))
                        .requestMatchers("/api/chargebacks/acquisition", "/api/chargebacks/acquisition/**")
                        .hasAuthority(menu("CHARGEBACK_ACQUISITION"))
                        .requestMatchers("/api/reporting/kpi", "/api/reporting/kpi/**").hasAuthority(menu("REPORTING_KPI"))
                        .requestMatchers("/api/reporting/reglementaire/**").hasAuthority(menu("REPORTING_REGLEMENTAIRE"))
                        .requestMatchers(HttpMethod.GET, "/api/parametrage/**").hasAuthority(u)
                        .requestMatchers("/api/parametrage/gab/**").hasAuthority(menu("PARAMETRAGE_GAB"))
                        .requestMatchers("/api/parametrage/vss/**").hasAuthority(menu("PARAMETRAGE_VSS"))
                        .requestMatchers("/api/parametrage/**").hasAuthority(menu("PARAMETRAGE_LIS"))
                        .requestMatchers("/api/systeme/**").hasAuthority(u)
                        .requestMatchers("/api/**").denyAll()
                        .anyRequest().permitAll());
        return http.build();
    }
}
