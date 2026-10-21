# ADR 0002 – Authentification par session serveur et habilitations par menus

- **Date** : 2026-10-21
- **Statut** : accepté

## Contexte

MONETICA V2 gère des comptes locaux (login / mot de passe), des profils définis par un
arbre de menus, le verrouillage après 3 échecs et une piste d'audit. RAPMONET est une
application web interne servie sur un seul domaine (Angular + API).

## Décision

- **Session côté serveur** (cookie `JSESSIONID` HttpOnly, SameSite=Strict, 30 min) plutôt
  qu'un jeton JWT stocké dans le navigateur : rien d'exploitable n'est accessible au JavaScript,
  et la déconnexion ou la désactivation d'un compte est immédiate.
- **Protection CSRF** par cookie `XSRF-TOKEN` / en-tête `X-XSRF-TOKEN`, lue nativement par Angular.
- Mots de passe hachés en **BCrypt** ; politique : 8 caractères minimum, au moins une lettre et un chiffre.
- **Habilitations = menus** (table `securite_menu`) : chaque menu autorisé par le profil donne
  l'autorisation `MENU_<code>` ; chaque API est réservée à un menu dans `ConfigurationSecurite`.
  Toute API non déclarée est refusée (`/api/**` → `denyAll`).
- L'utilisateur est **rechargé à chaque requête** : un changement de profil s'applique sans
  reconnexion, un compte désactivé ou verrouillé perd son accès immédiatement.
- Piste d'audit écrite dans une **transaction séparée** : un événement est conservé même si
  l'action échoue.

## Conséquences

- Un nouvel écran exige : un code menu (migration), une règle dans `ConfigurationSecurite`,
  l'entrée correspondante dans `frontend/src/app/core/menu.ts` et la route avec `data.menu`.
- Évolutions possibles sans changer ce modèle : annuaire LDAP / Active Directory de la banque,
  double authentification.
