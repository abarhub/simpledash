package com.simpledash.lib;

import java.util.List;

// Chaque champ est une liste dédupliquée (valeur du répertoire courant
// d'abord, puis celles des modules dans leur ordre) plutôt qu'une valeur
// scalaire, pour qu'une version différente sur un sous-module s'ajoute au
// lieu d'écraser celle de la racine.
public record ProjectSummary(
    List<String> javaVersion,
    List<String> springBootVersion,
    List<String> angularVersion,
    List<String> rustVersion,
    List<String> goVersion
) {}
