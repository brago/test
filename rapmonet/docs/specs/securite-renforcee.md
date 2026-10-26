# Sécurité renforcée : politique de mot de passe et double authentification

## Politique de mot de passe (écran « Politique de sécurité », menu `SECURITE_POLITIQUE`)

Paramètres `securite.*` de `parametre_application`, modifiables à l'écran (audit `POLITIQUE_SECURITE`) :

| Paramètre | Défaut | Bornes |
|---|---|---|
| Longueur minimale | 10 | 8 à 64 |
| Majuscule / minuscule / chiffre / caractère spécial exigés | oui / oui / oui / non | au moins majuscule ou minuscule |
| Validité du mot de passe | 90 jours | 0 (sans expiration) à 730 |
| Historique (derniers mots de passe non réutilisables) | 5 | 0 à 24 |
| Échecs consécutifs avant verrouillage (mot de passe ou code) | 3 | 3 à 10 |
| Double authentification obligatoire | non | |

- Le mot de passe ne peut jamais contenir l'identifiant.
- La composition est contrôlée à la création, à la réinitialisation par l'administrateur et au changement. L'historique (`securite_mdp_historique`) n'est contrôlé qu'au changement par l'utilisateur (le mot de passe temporaire de l'administrateur y échappe).
- Expiration : à la connexion suivant l'échéance (`mdp_modifie_le` + validité), le changement est imposé. L'en-tête affiche le nombre de jours restants dans les 10 derniers jours.

## Double authentification (TOTP, RFC 6238)

- Application du téléphone : Google Authenticator, Microsoft Authenticator, FreeOTP… (code à 6 chiffres renouvelé toutes les 30 secondes, HMAC-SHA1).
- Activation par l'utilisateur (menu du compte → Double authentification) : QR code généré par le serveur (SVG, aucun service externe), puis saisie d'un premier code. Le secret est chiffré en base (AES-GCM, clé `RAPMONET_CLE_CHIFFREMENT`, **à définir en production** ; la changer rend les secrets illisibles : réinitialiser alors la double authentification des utilisateurs).
- Connexion : mot de passe, puis code (`POST /api/auth/code`) dans les 5 minutes. Décalage d'horloge toléré : ±30 secondes. Un code déjà utilisé est refusé. Les codes faux comptent dans les échecs avant verrouillage.
- Obligatoire : un utilisateur sans double authentification ne peut rien faire d'autre que l'activer (habilitation `ACTIVATION_2FA_REQUISE`) ; il ne peut plus la désactiver.
- Téléphone perdu : l'administrateur la réinitialise depuis l'écran Utilisateurs (audit `DEUX_FACTEURS_REINITIALISATION`).

**Avant de rendre la double authentification obligatoire, l'administrateur doit l'avoir activée sur son propre compte.**
