# Génération du paramétrage LIS à partir de la spécification

La migration `backend/src/main/resources/db/migration/V3__parametrage_lis_gim.sql` est
**générée** à partir de la spécification GIM/HPS « LIS 3.038 » (Annexe B). Pour une
nouvelle version de la spécification :

```bash
pdftotext -layout "LIS 3 0xx.pdf" lis.txt
python3 extraire_spec.py lis.txt spec.json          # extrait et contrôle les tableaux de champs
python3 generer_sql.py spec.json V<n>__parametrage_lis_3_0xx.sql
```

`extraire_spec.py` affiche, pour chaque section, `OK` si les champs se suivent sans trou
ni chevauchement jusqu'à la position 256, sinon les écarts détectés. Seules les sections
`OK` listées dans `STRUCTURES` (dans `generer_sql.py`) sont reprises.

Une migration déjà appliquée ne doit jamais être modifiée : écrire une **nouvelle**
migration qui met à jour le paramétrage.

La spécification elle-même est confidentielle (GIM/HPS) et n'est pas versionnée.
