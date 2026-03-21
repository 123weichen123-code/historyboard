package com.historyboard.ingest.connectors;

import com.historyboard.model.SourceCitation;
import java.io.IOException;
import java.util.Optional;

public interface CitationConnector {
    String name();

    Optional<SourceCitation> fetchCitation(String keyword) throws IOException, InterruptedException;
}
