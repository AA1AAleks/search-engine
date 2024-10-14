package searchengine.service.indexing;

import searchengine.dto.indexing.IndexingRequest;
import searchengine.dto.indexing.IndexingResponse;

public interface IndexingService {

    IndexingResponse startIndexing();

    IndexingResponse stopIndexing();

    IndexingResponse indexPage(IndexingRequest indexingRequest);
}
