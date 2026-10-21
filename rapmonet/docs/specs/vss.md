# Rapports VSS (fichiers « VX ») – notes d'implémentation

Source : deux fichiers VX réels (deux journées de traitement), sans spécification. Ces fichiers ne contiennent **pas** de numéro de carte : ce sont les rapports de
règlement VisaNet Settlement Service imprimés (fichier Visa **EP-747**), que le GIM nomme
`<BANQUE>_VX747[_n].txt`.

## Format

- Texte de 133 caractères par ligne. Le **1er caractère** est un code de contrôle d'impression :
  `\f` (ou `1`) = nouvelle page ; il est retiré avant analyse.
- Chaque page commence par `REPORT ID: VSS-xxx … PAGE: n`, puis un en-tête :
  `REPORTING FOR` (SRE), `ROLLUP TO`, `FUNDS XFER ENTITY`, `PROC DATE`, `REPORT DATE`,
  `SETTLEMENT CURRENCY`, `CLEARING CURRENCY`, puis les titres de colonnes.
- Corps : libellés hiérarchisés par indentation, suivis de valeurs alignées sous les colonnes.
  Montants : `1,430,040DB`, `9.62CR`, `0.00` ; DB = débit, CR = crédit.
- Chaque rapport se termine par `*** END OF VSS-xxx REPORT ***` ; un rapport vide contient
  `*** NO DATA FOR THIS REPORT ***`.

## Lecture (générique, `ingestion.vss.LecteurRapportsVss`)

- **Colonnes déduites des titres** : les mots des lignes de titres qui se chevauchent
  horizontalement forment une colonne (« CREDIT » + « AMOUNT » → `CREDIT AMOUNT`) ; une bannière
  `*****SETTLEMENT CURRENCY*****` qualifie les colonnes qu'elle couvre (VSS-210).
  Chaque valeur est rangée dans la colonne qui la chevauche le plus.
- **Hiérarchie** reconstruite à partir de l'indentation (`chemin` = libellés parents).
- Lignes particulières : valeurs sans libellé = suite du libellé précédent (autre table de taux,
  VSS-120) ; libellé seul puis valeurs sur la ligne suivante (VSS-210) = une seule ligne ;
  libellé seul = titre de section, conservé sans valeur.
- La devise de compensation est lue **par page** (VSS-900-S : XOF, NONE, ALL).
- Un nouveau type de rapport Visa est lu sans modification du code.

## Contrôles

| Contrôle | Gravité |
|---|---|
| Fichier déjà intégré (SHA-256) | Bloquant |
| Aucun rapport / dernier rapport sans ligne de fin (fichier tronqué) | Bloquant |
| PROC DATE introuvable | Bloquant |
| Rapport sans ligne de fin suivi d'un autre rapport | Avertissement |
| Plusieurs dates de traitement dans le fichier | Avertissement |
| Rapport non paramétré (absent de `vss_type_rapport`) | Avertissement (enregistré tel quel) |
| Autre fichier déjà intégré pour la même date et le même SRE | Avertissement |

Paramétrage › Rapports VSS : choix des rapports à enregistrer (équivalent des fichiers EP* cochés de MONETICA).

## Synthèse calculée à l'intégration

| Valeur | Source |
|---|---|
| Net à régler (signé, devise de règlement) | VSS-110, ligne `NET SETTLEMENT AMOUNT`, colonne `TOTAL AMOUNT` |
| Nombre de transactions | VSS-110, ligne `TOTAL INTERCHANGE VALUE`, colonne `COUNT` |
| Montant compensé (signé, devise de compensation) | VSS-900-S, ligne `TOTAL SENT TO SETTLEMENT` de la 1re page en devise réelle |

Vérifié sur les deux fichiers reçus : valeurs identiques à celles imprimées dans les rapports VSS-110 et VSS-900-S.

## Reste à faire

- Rapprochement LIS / VSS (couverture Visa, lot 6) : comparer le montant compensé VSS-900-S aux
  transactions Visa du LIS (sous-fichier FV) de la journée correspondante.
- Si le GIM transmet aussi les fichiers EP transactionnels (TC 05/06/07 Visa BASE II), les intégrer
  avec le moteur positionnel du LIS.
