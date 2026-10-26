package com.rapmonet.reclamation.internal;

import com.rapmonet.chargeback.DossiersPorteur;
import com.rapmonet.chargeback.DossiersPorteur.DossierPorteur;
import com.rapmonet.commun.web.RegleMetierException;
import com.rapmonet.ingestion.ConsultationJournaux;
import com.rapmonet.ingestion.ConsultationLis;
import com.rapmonet.ingestion.OperationsPorteur;
import com.rapmonet.rapprochement.StatutsRetraits;
import com.rapmonet.securite.ActionsAudit;
import com.rapmonet.securite.JournalAudit;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Recherche transverse des opérations d'un porteur : retraits et soldes des journaux GAB (avec leur statut de
 * rapprochement GAB ↔ LIS), transactions LIS, chargebacks et réclamations déjà enregistrées.
 *
 * <p>Le numéro de carte complet, s'il est saisi, sert seulement à calculer son empreinte (correspondance exacte dans
 * les LIS et les chargebacks) : il n'est ni conservé ni écrit dans la piste d'audit, qui reçoit la carte masquée.
 */
@Service
public class RecherchePorteur {

    static final int MAXIMUM = 300;

    public record Critere(String carte, String compte, String reference, LocalDate du, LocalDate au) {
    }

    /** Carte saisie, réduite à ce qui est conservé et affiché. */
    record Carte(String debut, String fin, String empreinte) {
        String masquee() {
            return debut + "******" + fin;
        }
    }

    public record OperationGab(ConsultationJournaux.Operation operation, StatutsRetraits.Statut rapprochement) {
    }

    public record TransactionLis(long id, LocalDate journee, String typeLis, String fichier, String tc, String libelleTc,
                                 LocalDate date, Number montant, String carte, String libelle, String terminal,
                                 String autorisation, String reference) {
    }

    /**
     * Résultat de la recherche.
     *
     * @param carteMasquee  carte recherchée (6 premiers et 4 derniers chiffres), si une carte a été saisie
     * @param exacte        numéro complet saisi : correspondance exacte dans les LIS et les chargebacks
     * @param tronque       au moins une liste a atteint le maximum affiché
     */
    public record Resultat(String carteMasquee, boolean exacte, String compte, String reference, LocalDate du,
                           LocalDate au, List<OperationGab> operationsGab, List<TransactionLis> transactionsLis,
                           List<DossierPorteur> chargebacks, List<ReclamationsService.Resume> reclamations,
                           boolean tronque) {
    }

    private final OperationsPorteur operations;
    private final DossiersPorteur chargebacks;
    private final StatutsRetraits statuts;
    private final ReclamationsService reclamations;
    private final JournalAudit audit;

    RecherchePorteur(OperationsPorteur operations, DossiersPorteur chargebacks, StatutsRetraits statuts,
                     ReclamationsService reclamations, JournalAudit audit) {
        this.operations = operations;
        this.chargebacks = chargebacks;
        this.statuts = statuts;
        this.reclamations = reclamations;
        this.audit = audit;
    }

    public Resultat rechercher(Critere c) {
        Carte carte = carte(c.carte(), operations::empreinte);
        String compte = vide(c.compte());
        String reference = vide(c.reference());
        if (carte == null && compte == null && reference == null) {
            throw new RegleMetierException("Indiquez une carte, un compte ou une référence (ticket, autorisation, RRN, ARN).");
        }
        LocalDate au = c.au() != null ? c.au() : LocalDate.now();
        LocalDate du = c.du() != null ? c.du() : au.minusYears(1);
        if (du.isAfter(au) || ChronoUnit.DAYS.between(du, au) > 731) {
            throw new RegleMetierException("Période invalide : deux ans au plus.");
        }
        OperationsPorteur.Critere critere = new OperationsPorteur.Critere(du, au, carte == null ? null : carte.debut(),
                carte == null ? null : carte.fin(), carte == null ? null : carte.empreinte(), compte, reference);

        List<ConsultationJournaux.Operation> gab = operations.operationsGab(critere, MAXIMUM);
        List<StatutsRetraits.Retrait> retraits = gab.stream()
                .filter(o -> "RETRAIT".equals(o.type()) && "DISTRIBUE".equals(o.statut()))
                .map(o -> new StatutsRetraits.Retrait(o.id(), o.terminal(), o.dateHeure(), o.carte(), o.reference(), o.montant()))
                .toList();
        Map<Long, StatutsRetraits.Statut> s = statuts.statuts(retraits);
        List<OperationGab> operationsGab = gab.stream().map(o -> new OperationGab(o, s.get(o.id()))).toList();

        // Le compte n'est pas dans les LIS : une recherche par compte s'étend aux cartes vues sur ses tickets.
        List<ConsultationLis.TransactionLis> lis = new ArrayList<>(operations.transactionsLis(critere, MAXIMUM));
        if (carte == null && compte != null) {
            gab.stream().map(ConsultationJournaux.Operation::carte).filter(x -> x != null && x.replaceAll("\\D", "").length() == 10)
                    .distinct().limit(5).forEach(x -> {
                        String chiffres = x.replaceAll("\\D", "");
                        lis.addAll(operations.transactionsLis(new OperationsPorteur.Critere(du, au, chiffres.substring(0, 6),
                                chiffres.substring(6), null, null, reference), MAXIMUM));
                    });
        }
        List<TransactionLis> transactions = lis.stream().map(t -> new TransactionLis(t.id(), t.journee(), t.typeLis().name(),
                t.nomFichier(), t.tc(), t.libelleTc(), t.synthese().date(), t.synthese().montant(), t.synthese().carte(),
                t.synthese().libelle(), t.synthese().terminal(), t.synthese().autorisation(), t.synthese().reference()))
                .distinct().toList();
        List<DossierPorteur> cb = chargebacks.dossiers(carte == null ? null : carte.debut(), carte == null ? null : carte.fin(),
                carte == null ? null : carte.empreinte(), reference, du, au);
        List<ReclamationsService.Resume> recs = reclamations.duPorteur(carte == null ? null : carte.masquee(), compte);

        audit.enregistrer(ActionsAudit.RECHERCHE_PORTEUR, carte != null ? "Carte " + carte.masquee()
                        : compte != null ? "Compte " + compte : "Référence " + reference,
                "Du " + du + " au " + au + " : " + gab.size() + " opération(s) GAB, " + transactions.size() + " LIS, "
                        + cb.size() + " chargeback(s)");
        return new Resultat(carte == null ? null : carte.masquee(), carte != null && carte.empreinte() != null, compte,
                reference, du, au, operationsGab, transactions, cb, recs,
                gab.size() >= MAXIMUM || lis.size() >= MAXIMUM);
    }

    /**
     * Numéro complet (13 à 19 chiffres, clé de Luhn) ou 6 premiers et 4 derniers chiffres (10 chiffres, ou forme masquée
     * 459847******1234). Null si rien n'est saisi.
     */
    static Carte carte(String saisie, java.util.function.UnaryOperator<String> empreinte) {
        if (saisie == null || saisie.isBlank()) {
            return null;
        }
        String s = saisie.strip();
        String chiffres = s.replaceAll("\\D", "");
        boolean masquee = s.matches("\\d{6}[*_xX\\s-]+\\d{4}");
        if (!masquee && chiffres.length() >= 13 && chiffres.length() <= 19) {
            if (!luhn(chiffres)) {
                throw new RegleMetierException("Numéro de carte invalide (clé de contrôle) : vérifiez la saisie.");
            }
            return new Carte(chiffres.substring(0, 6), chiffres.substring(chiffres.length() - 4), empreinte.apply(chiffres));
        }
        if (chiffres.length() == 10) {
            return new Carte(chiffres.substring(0, 6), chiffres.substring(6), null);
        }
        throw new RegleMetierException("Carte : saisissez le numéro complet, ou les 6 premiers et les 4 derniers chiffres "
                + "(ex. 459847******1234).");
    }

    static boolean luhn(String chiffres) {
        int somme = 0;
        boolean double_ = false;
        for (int i = chiffres.length() - 1; i >= 0; i--) {
            int d = chiffres.charAt(i) - '0';
            if (double_) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            somme += d;
            double_ = !double_;
        }
        return somme % 10 == 0;
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
