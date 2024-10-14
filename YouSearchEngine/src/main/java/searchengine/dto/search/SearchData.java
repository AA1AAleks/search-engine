package searchengine.dto.search;

import searchengine.model.PageEntity;

public record SearchData(String site, String siteName, String uri,
                         String title, String snippet, float relevance) {
    public SearchData(PageEntity page, String title, String snippet, float relevance) {
        this(page.getSite().getUrl(), page.getSite().getName(), page.getPath(),
                title, snippet, relevance);
    }
}
