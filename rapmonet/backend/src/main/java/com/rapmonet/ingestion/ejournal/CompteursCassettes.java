package com.rapmonet.ingestion.ejournal;

import java.util.List;

/** Bloc « CASH TOTAL » imprimé après un retrait : une valeur par type de cassette (TYPE1 à TYPE4). */
public record CompteursCassettes(List<Integer> coupures, List<Integer> distribues, List<Integer> rejetes,
                                 List<Integer> restants) {
}
