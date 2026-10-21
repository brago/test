package com.rapmonet.parametrage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Description complète d'un format de fichier positionnel (ex. LIS du GIM).
 *
 * @param zoneTc         emplacement du code TC dans chaque ligne
 * @param zoneSequence   emplacement du numéro de séquence
 * @param zoneTcr        emplacement du numéro de TCR
 * @param positionDonnees première position décrite par les attributs des TCR
 * @param tcs            TC connus, indexés par code
 */
public record FormatFichier(String code, String famille, String libelle, int longueurEnregistrement,
                            Zone zoneTc, Zone zoneSequence, Zone zoneTcr, int positionDonnees,
                            String tcEntete, String tcFin,
                            List<SousFichierFormat> sousFichiers, Map<String, TcFormat> tcs) {

    public Optional<TcFormat> tc(String code) {
        return Optional.ofNullable(tcs.get(code));
    }

    public Optional<SousFichierFormat> sousFichierParEntete(String tc) {
        return sousFichiers.stream().filter(s -> s.tcEntete().equals(tc)).findFirst();
    }

    public Optional<SousFichierFormat> sousFichierParFin(String tc) {
        return sousFichiers.stream().filter(s -> s.tcFin().equals(tc)).findFirst();
    }
}
