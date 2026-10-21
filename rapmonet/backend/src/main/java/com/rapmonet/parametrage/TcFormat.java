package com.rapmonet.parametrage;

import java.util.List;
import java.util.Optional;

/** Transaction Code et ses TCR. */
public record TcFormat(String code, String libelle, String description, int longueur, List<TcrFormat> tcrs) {

    public Optional<TcrFormat> tcr(String codeTcr) {
        return tcrs.stream().filter(t -> t.code().equals(codeTcr)).findFirst();
    }
}
