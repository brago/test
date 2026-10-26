package com.rapmonet.securite;

/** Codes des actions enregistrées dans la piste d'audit. */
public final class ActionsAudit {

    public static final String CONNEXION = "CONNEXION";
    public static final String DECONNEXION = "DECONNEXION";
    public static final String VERROUILLAGE = "VERROUILLAGE";
    public static final String CHANGEMENT_MOT_DE_PASSE = "CHANGEMENT_MOT_DE_PASSE";
    public static final String DEUX_FACTEURS_ACTIVATION = "DEUX_FACTEURS_ACTIVATION";
    public static final String DEUX_FACTEURS_DESACTIVATION = "DEUX_FACTEURS_DESACTIVATION";
    public static final String DEUX_FACTEURS_REINITIALISATION = "DEUX_FACTEURS_REINITIALISATION";
    public static final String POLITIQUE_SECURITE = "POLITIQUE_SECURITE";
    public static final String UTILISATEUR_CREATION = "UTILISATEUR_CREATION";
    public static final String UTILISATEUR_MODIFICATION = "UTILISATEUR_MODIFICATION";
    public static final String UTILISATEUR_DEVERROUILLAGE = "UTILISATEUR_DEVERROUILLAGE";
    public static final String UTILISATEUR_REINITIALISATION_MDP = "UTILISATEUR_REINITIALISATION_MDP";
    public static final String PROFIL_CREATION = "PROFIL_CREATION";
    public static final String PROFIL_MODIFICATION = "PROFIL_MODIFICATION";
    public static final String PROFIL_SUPPRESSION = "PROFIL_SUPPRESSION";
    public static final String LIS_INTEGRATION = "LIS_INTEGRATION";
    public static final String LIS_ANNULATION = "LIS_ANNULATION";
    public static final String VSS_INTEGRATION = "VSS_INTEGRATION";
    public static final String VSS_ANNULATION = "VSS_ANNULATION";
    public static final String PARAMETRAGE_VSS = "PARAMETRAGE_VSS";
    public static final String EJOURNAL_INTEGRATION = "EJOURNAL_INTEGRATION";
    public static final String EJOURNAL_ANNULATION = "EJOURNAL_ANNULATION";
    public static final String BATCH_EJOURNAL = "BATCH_EJOURNAL";
    public static final String BATCH_LIS = "BATCH_LIS";
    public static final String PARAMETRAGE_BATCH = "PARAMETRAGE_BATCH";
    public static final String PARAMETRAGE_GAB = "PARAMETRAGE_GAB";
    public static final String GAB_APPRO_SAISIE = "GAB_APPRO_SAISIE";
    public static final String GAB_APPRO_VALIDATION = "GAB_APPRO_VALIDATION";
    public static final String GAB_APPRO_ANNULATION = "GAB_APPRO_ANNULATION";
    public static final String GAB_ARRETE_SAISIE = "GAB_ARRETE_SAISIE";
    public static final String GAB_ARRETE_VALIDATION = "GAB_ARRETE_VALIDATION";
    public static final String GAB_ARRETE_ANNULATION = "GAB_ARRETE_ANNULATION";
    public static final String CHARGEBACK_REGULARISATION = "CHARGEBACK_REGULARISATION";
    public static final String CHARGEBACK_ARCHIVAGE = "CHARGEBACK_ARCHIVAGE";
    public static final String PARAMETRAGE_COMPENSE = "PARAMETRAGE_COMPENSE";
    public static final String COMPENSE_CREATION = "COMPENSE_CREATION";
    public static final String COMPENSE_VALIDATION = "COMPENSE_VALIDATION";
    public static final String COMPENSE_ANNULATION = "COMPENSE_ANNULATION";
    public static final String RAPPROCHEMENT_ECART = "RAPPROCHEMENT_ECART";
    public static final String RAPPROCHEMENT_REGLES = "RAPPROCHEMENT_REGLES";
    public static final String EXPORT = "EXPORT";

    private ActionsAudit() {
    }
}
