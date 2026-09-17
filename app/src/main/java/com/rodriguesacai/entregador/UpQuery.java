package com.rodriguesacai.entregador;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resultado de consulta que pode combinar documentos legados e Supabase. */
public final class UpQuery {
    private final List<UpDocument> documents;

    public UpQuery(List<UpDocument> documents) {
        this.documents = documents == null ? new ArrayList<>() : new ArrayList<>(documents);
    }

    public List<UpDocument> getDocuments() {
        return Collections.unmodifiableList(documents);
    }
}
