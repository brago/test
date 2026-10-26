package com.rapmonet.alertes.internal;

import com.rapmonet.alertes.Alerte;
import com.rapmonet.alertes.SourceAlertes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Rassemble les alertes de tous les modules ; une source en échec n'empêche pas les autres. */
@Service
public class AlertesService {

    private static final Logger LOG = LoggerFactory.getLogger(AlertesService.class);

    private final List<SourceAlertes> sources;
    private final Clock horloge;

    AlertesService(List<SourceAlertes> sources) {
        this.sources = sources;
        this.horloge = Clock.systemDefaultZone();
    }

    /** Alertes visibles avec les menus donnés, des plus graves aux moins graves. */
    public List<Alerte> alertes(Set<String> menus) {
        LocalDate aujourdhui = LocalDate.now(horloge);
        List<Alerte> toutes = new ArrayList<>();
        for (SourceAlertes s : sources) {
            try {
                toutes.addAll(s.alertes(aujourdhui));
            } catch (RuntimeException e) {
                LOG.warn("Source d'alertes {} en échec : {}", s.getClass().getSimpleName(), e.getMessage());
            }
        }
        return toutes.stream()
                .filter(a -> a.nombre() > 0 && menus.contains(a.menu()))
                .sorted(Comparator.comparing(Alerte::niveau).thenComparing(Alerte::code))
                .toList();
    }
}
