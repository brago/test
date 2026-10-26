# Alertes

Les situations qui demandent une action s'affichent sur l'**accueil** (carte « À traiter ») ; une **cloche** dans
l'en-tête compte les alertes critiques et à traiter. Chaque utilisateur ne voit que les alertes des menus de son
profil (filtrage côté serveur, `GET /api/alertes`). Les alertes sont recalculées à l'ouverture de l'accueil, au
bouton « Actualiser » et toutes les 5 minutes ; rien n'est stocké.

## Architecture

Module `alertes` : `Alerte` (code, niveau CRITIQUE / ATTENTION / INFO, titre, détail, nombre, montant, menu, lien)
et l'interface `SourceAlertes`, implémentée dans chaque module concerné (`internal/Alertes*`). Une source en échec
est ignorée (journalisée) sans bloquer les autres. Une alerte de nombre 0 n'est pas affichée.

## Alertes livrées

| Code | Niveau | Règle | Menu |
|---|---|---|---|
| CHARGEBACK_RECU_ECHEANCE | Critique | Chargeback reçu en attente, échéance dans 7 jours ou moins | CHARGEBACK_ACQUISITION |
| CHARGEBACK_RECU_DEFINITIF | Attention | Chargeback reçu, délai dépassé, non régularisé | CHARGEBACK_ACQUISITION |
| CHARGEBACK_EMIS_ACQUIS | Attention | Chargeback émis sans représentation dans le délai, non régularisé | CHARGEBACK_EMISSION |
| GAB_APPRO_A_VALIDER / GAB_ARRETE_A_VALIDER | Info, Attention si plus d'un jour | Saisies au statut « À valider » | GAB_VALIDATION |
| GAB_SANS_JOURNAL | Attention | GAB actif sans journal intégré la veille | CHARGEMENT_EJOURNAL |
| BATCH_JOURNAUX_ERREUR | Critique | Dernière exécution du batch en erreur | CHARGEMENT_EJOURNAL |
| BATCH_JOURNAUX_REJETS | Attention | Dernière exécution avec des fichiers rejetés | CHARGEMENT_EJOURNAL |
| LIS_AUCUN / LIS_EN_RETARD | Info / Attention | Aucun fichier LIS, ou dernière journée de plus de 3 jours | CHARGEMENT_LIS |
| RAPPRO_NON_REMONTES | Attention | Retraits confrères non remontés, non traités (60 derniers jours) | RAPPROCHEMENT_GAB_LIS |
| RAPPRO_AVIS_SANS_RETRAIT | Attention | Avis LIS sans retrait, non traités (60 jours) | RAPPROCHEMENT_GAB_LIS |
| RAPPRO_JOURNAL_ABSENT | Info | Avis sur un GAB sans journal (60 jours) | RAPPROCHEMENT_GAB_LIS |
| COMPENSE_A_VALIDER | Attention | Traitements de compense à valider | COMPENSE_VALIDATION |

Seuils (7 jours, 3 jours, 60 jours) : constantes des sources, à rendre paramétrables si la banque le souhaite.

## À venir

Envoi par e-mail (résumé quotidien) dès qu'un serveur SMTP de la banque est disponible.
- `GAB_ACTIVITE_ANORMALE` (attention, menu GAB_ACTIVITE) : GAB signalés la veille par l'activité des GAB (refus élevés, inactivité, cartes capturées).
