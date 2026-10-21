package com.rapmonet.parametrage.internal;

import com.rapmonet.parametrage.AttributFormat;
import com.rapmonet.parametrage.FormatFichier;
import com.rapmonet.parametrage.FormatsFichiers;
import com.rapmonet.parametrage.Pad;
import com.rapmonet.parametrage.SousFichierFormat;
import com.rapmonet.parametrage.TcFormat;
import com.rapmonet.parametrage.TcrFormat;
import com.rapmonet.parametrage.TypeDonnee;
import com.rapmonet.parametrage.Zone;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
class FormatsFichiersJdbc implements FormatsFichiers {

    private final JdbcClient jdbc;

    FormatsFichiersJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ResumeFormat> lister() {
        return jdbc.sql("select code, famille, libelle from fmt_fichier order by code")
                .query(ResumeFormat.class).list();
    }

    @Override
    public Optional<FormatFichier> trouver(String code) {
        record LigneFichier(long id, String famille, String libelle, int longueurEnregistrement,
                            int positionTc, int longueurTc, int positionSequence, int longueurSequence,
                            int positionTcr, int longueurTcr, int positionDonnees, String tcEntete, String tcFin) {
        }
        return jdbc.sql("select * from fmt_fichier where code = ?").param(code)
                .query(LigneFichier.class).optional()
                .map(f -> new FormatFichier(code, f.famille(), f.libelle(), f.longueurEnregistrement(),
                        new Zone(f.positionTc(), f.longueurTc()),
                        new Zone(f.positionSequence(), f.longueurSequence()),
                        new Zone(f.positionTcr(), f.longueurTcr()),
                        f.positionDonnees(), f.tcEntete(), f.tcFin(),
                        sousFichiers(f.id()), tcs(f.famille(), f.positionDonnees())));
    }

    private List<SousFichierFormat> sousFichiers(long fichierId) {
        record Ligne(long id, String code, String libelle, String description, String tcEntete, String tcFin) {
        }
        List<Ligne> lignes = jdbc.sql("""
                        select s.id, s.code, s.libelle, s.description, s.tc_entete, s.tc_fin
                        from fmt_fichier_sous_fichier fs join fmt_sous_fichier s on s.id = fs.sous_fichier_id
                        where fs.fichier_id = ? order by fs.rang""")
                .param(fichierId).query(Ligne.class).list();
        List<SousFichierFormat> resultat = new ArrayList<>();
        for (Ligne l : lignes) {
            List<String> tcAutorises = jdbc.sql("""
                            select tc.code from fmt_sous_fichier_tc st join fmt_tc tc on tc.id = st.tc_id
                            where st.sous_fichier_id = ? order by st.rang""")
                    .param(l.id()).query(String.class).list();
            resultat.add(new SousFichierFormat(l.code(), l.libelle(), l.description(), l.tcEntete(), l.tcFin(), tcAutorises));
        }
        return resultat;
    }

    private Map<String, TcFormat> tcs(String famille, int positionDonnees) {
        record LigneAttribut(String tc, String tcr, String code, String libelle, int taille, String pad,
                             int decimales, String typeDonnee, boolean specifique,
                             boolean confidentiel) {
        }
        Map<String, List<LigneAttribut>> attributsParTcr = new LinkedHashMap<>();
        jdbc.sql("""
                        select tc.code as tc, tcr.code as tcr, a.code, a.libelle, a.taille, a.pad,
                               a.decimales, a.type_donnee, a.specifique, a.confidentiel
                        from fmt_tcr_attribut ta
                        join fmt_tcr tcr on tcr.id = ta.tcr_id
                        join fmt_tc tc on tc.id = tcr.tc_id
                        join fmt_attribut a on a.id = ta.attribut_id
                        where tc.famille = ? order by tc.code, tcr.rang, ta.rang""")
                .param(famille).query(LigneAttribut.class).list()
                .forEach(a -> attributsParTcr.computeIfAbsent(a.tc() + "/" + a.tcr(), k -> new ArrayList<>()).add(a));

        record LigneTcr(String tc, String libelleTc, String description, int longueur, String tcr,
                        String libelleTcr, int rang) {
        }
        Map<String, TcFormat> tcs = new LinkedHashMap<>();
        jdbc.sql("""
                        select tc.code as tc, tc.libelle as libelle_tc, tc.description, tc.longueur,
                               tcr.code as tcr, tcr.libelle as libelle_tcr, tcr.rang
                        from fmt_tc tc left join fmt_tcr tcr on tcr.tc_id = tc.id
                        where tc.famille = ? order by tc.code, tcr.rang""")
                .param(famille).query(LigneTcr.class).list()
                .forEach(l -> {
                    TcFormat tc = tcs.computeIfAbsent(l.tc(),
                            k -> new TcFormat(l.tc(), l.libelleTc(), l.description(), l.longueur(), new ArrayList<>()));
                    if (l.tcr() != null) {
                        List<AttributFormat> attributs = new ArrayList<>();
                        int position = positionDonnees;
                        for (LigneAttribut a : attributsParTcr.getOrDefault(l.tc() + "/" + l.tcr(), List.of())) {
                            attributs.add(new AttributFormat(a.code(), a.libelle(), position, a.taille(),
                                    Pad.valueOf(a.pad()), a.decimales(), TypeDonnee.valueOf(a.typeDonnee()), a.specifique(),
                                    a.confidentiel()));
                            position += a.taille();
                        }
                        tc.tcrs().add(new TcrFormat(l.tcr(), l.libelleTcr(), l.rang(), List.copyOf(attributs)));
                    }
                });
        return tcs;
    }
}
