# Jeu de démonstration (données fictives)

À charger dans une installation de démonstration pour tester les écrans sans données bancaires réelles.

| Fichier | Écran | Contenu |
|---|---|---|
| `99990001_20260625.jrn` | Chargement › Journaux GAB | Journal NCR anonymisé du GAB fictif 99990001 (25/06/2026) ; les tickets de deux comptes fictifs n'impriment pas de compte : ce sont les retraits de cartes d'autres banques. |
| `LIS_IN_DEMO_SW_18126_01` | Chargement › Fichiers LIS (case LIS SW) | Fichier LIS synthétique (journée du 30/06/2026) : avis de retraits GAB (TC 49, 6011) fabriqués à partir du journal. |
| `LIS_IN_BIN_TEST_01` | Paramétrage › GAB, profils et BIN › onglet BIN | Table des BIN GIM synthétique. |

Puis *Rapprochements › GAB ↔ LIS*, du 24/06/2026 au 26/06/2026, vue « Tous » :

- 2 retraits rapprochés (l'un sur l'heure, l'autre sur la référence du ticket) ;
- 2 retraits non remontés par le GIM, dont un dont l'avis LIS porte 500 XOF de plus (il apparaît aussi en « avis LIS
  sans retrait ») : il devient rapproché après avoir fixé l'écart de montant toléré à 1 000 XOF dans
  *Règles de rapprochement* (à essayer d'abord en simulation) ;
- 1 autre avis LIS sans retrait au journal, 1 avis d'un GAB dont le journal n'est pas chargé.

Régénération : `cd backend && ./mvnw test -Dtest=DonneesDemoGenerateur -Ddemo.sortie=../demo`.
