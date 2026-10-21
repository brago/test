package com.rapmonet.ingestion.ejournal;

import com.rapmonet.ingestion.moteur.Gravite;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LecteurJournalNcrTest {

    private static String brut() throws IOException {
        try (InputStream in = LecteurJournalNcrTest.class.getResourceAsStream("/fichiers/ejournal/99990001_20260625.jrn")) {
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    private static ResultatLectureJournal lire() throws IOException {
        return new LecteurJournalNcr(LecteurJournalNcr.FORMAT_DATE_DEFAUT).lire(LecteurJournalNcr.lignes(brut()));
    }

    @Test
    void nettoieLesSequencesDImprimanteEtLesFinsDeLigneCr() throws IOException {
        List<String> lignes = LecteurJournalNcr.lignes(brut());
        assertThat(lignes).noneMatch(l -> l.indexOf('\u001b') >= 0 || l.indexOf('\r') >= 0);
        assertThat(lignes).contains(" CARD INSERTED");
    }

    @Test
    void litSessionsOperationsEtEvenement() throws IOException {
        ResultatLectureJournal r = lire();
        assertThat(r.anomalies()).isEmpty();
        assertThat(r.terminal()).isEqualTo("99990001");
        assertThat(r.sessions()).hasSize(25).allMatch(SessionGab::complete);
        assertThat(r.operations()).filteredOn(o -> o.type() == TypeOperationGab.RETRAIT).hasSize(22);
        assertThat(r.operations()).filteredOn(o -> o.type() == TypeOperationGab.SOLDE).hasSize(9);
        assertThat(r.evenements()).hasSize(1);
    }

    @Test
    void retraitApprouveAvecBilletsEtCompteurs() throws IOException {
        SessionGab s = lire().sessions().stream()
                .filter(x -> x.billetsPresentes() != null && x.operations().size() == 1).findFirst().orElseThrow();
        OperationGab o = s.operations().getFirst();
        assertThat(o.type()).isEqualTo(TypeOperationGab.RETRAIT);
        assertThat(o.codeReponse()).isEqualTo("00");
        assertThat(o.montant()).isPositive();
        assertThat(o.devise()).isEqualTo("XOF");
        assertThat(o.carte()).matches("400000_{6}\\d{4}");
        assertThat(o.compte()).startsWith("0999");
        assertThat(o.reference()).matches("\\d{6}");
        assertThat(o.dateHeure().toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 6, 25));
        assertThat(s.billetsPris()).isTrue();
        assertThat(s.compteurs().coupures()).containsExactly(5, 10, 20, 50);
        assertThat(s.compteurs().distribues()).hasSize(4);
    }

    @Test
    void retraitRefuseEtAnnulation() throws IOException {
        ResultatLectureJournal r = lire();
        OperationGab refuse = r.operations().stream().filter(o -> "91".equals(o.codeReponse())).findFirst().orElseThrow();
        assertThat(refuse.type()).isEqualTo(TypeOperationGab.RETRAIT);
        assertThat(refuse.montant()).isEqualTo(50000);
        assertThat(refuse.dateHeure()).isEqualTo(LocalDateTime.of(2026, 6, 25, 0, 13, 43));
        SessionGab annulee = r.sessions().stream().filter(s -> s.incidents().contains("Annulée par le client"))
                .findFirst().orElseThrow();
        assertThat(annulee.operations()).extracting(OperationGab::type)
                .containsExactly(TypeOperationGab.SOLDE, TypeOperationGab.RETRAIT);
        assertThat(annulee.operations().get(1).codeReponse()).isEqualTo("51");
        assertThat(annulee.billetsPresentes()).isNull();
    }

    @Test
    void demandeDeSoldeEtSessionsSansOperation() throws IOException {
        ResultatLectureJournal r = lire();
        OperationGab solde = r.operations().stream().filter(o -> o.type() == TypeOperationGab.SOLDE).findFirst().orElseThrow();
        assertThat(solde.codeReponse()).isEqualTo("00");
        assertThat(solde.compte()).startsWith("0999");
        assertThat(solde.dateHeure()).isNotNull();
        assertThat(solde.montant()).isNull();
        assertThat(r.sessions()).anyMatch(s -> s.operations().isEmpty());
    }

    @Test
    void evenementSuperviseurArreteEtAjoutDEspeces() throws IOException {
        EvenementGab e = lire().evenements().getFirst();
        assertThat(e.dateHeure()).isEqualTo(LocalDateTime.of(2026, 6, 25, 10, 13));
        assertThat(e.actions()).contains("REMISE_A_ZERO_COMPTEURS", "AJOUT_ESPECES");
        assertThat(e.especesAjoutees()).containsEntry("TYPE1", 3118).containsEntry("TYPE4", 0);
        assertThat(e.releves()).hasSize(2);
        assertThat(e.releves().getFirst().get("DISPENSED")).containsExactly(893, 454, 443, 0);
    }

    @Test
    void sessionCoupeeEtFichierQuiNEstPasUnJournal() {
        LecteurJournalNcr lecteur = new LecteurJournalNcr(LecteurJournalNcr.FORMAT_DATE_DEFAUT);
        ResultatLectureJournal coupe = lecteur.lire(List.of("     *TRANSACTION START*", " CARD INSERTED", "400000______1234"));
        assertThat(coupe.sessions()).singleElement().matches(s -> !s.complete());
        assertThat(coupe.anomalies()).singleElement().extracting(a -> a.gravite()).isEqualTo(Gravite.AVERTISSEMENT);

        assertThat(lecteur.lire(List.of("bonjour", "ceci n'est pas un journal")).estBloquant()).isTrue();
    }

    @Test
    void formatDeDateParametrable() {
        List<String> ticket = List.of("     *TRANSACTION START*", "CASH WITHRAWAL :400000______1234 NUM GAB :1",
                "22/05/26  10:00:00  123456", "AMOUNT :10000 XOF", "RESPONSE :00", " 10:00:05 TRANSACTION END");
        assertThat(new LecteurJournalNcr("dd/MM/yy").lire(ticket).operations().getFirst().dateHeure())
                .isEqualTo(LocalDateTime.of(2026, 5, 22, 10, 0));
        // Un mauvais format donne une date valide mais fausse : l'intégration contrôle sa vraisemblance.
        assertThat(new LecteurJournalNcr("yy/MM/dd").lire(ticket).operations().getFirst().dateHeure())
                .isEqualTo(LocalDateTime.of(2022, 5, 26, 10, 0));
    }

    @Test
    void masqueUnNumeroDeCarteEnClairMaisPasLesComptes() {
        String texte = "CARTE 4111111111111111\nACCOUNT :4111111111111111";
        assertThat(LecteurJournalNcr.masquerNumerosEnClair(texte))
                .isEqualTo("CARTE 411111******1111\nACCOUNT :4111111111111111");
        assertThat(LecteurJournalNcr.luhn("4111111111111111")).isTrue();
        assertThat(LecteurJournalNcr.luhn("4111111111111112")).isFalse();
    }
}
