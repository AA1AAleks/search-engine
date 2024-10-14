package searchengine.service.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.exceptions.BadRequestException;
import searchengine.exceptions.NotFoundException;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.SiteRepository;
import searchengine.util.HtmlParser;
import searchengine.util.LemmaParser;
import searchengine.util.SnippetGenerator;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private static final String URL_REGEX = "^https?://(?:www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\\b$";
    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final HtmlParser htmlParser;
    private final SnippetGenerator snippetGenerator;
    private final LemmaParser lemmaParser;

    @Override
    public SearchResponse search(String query, String site, Integer offset, Integer limit) {
        log.info("Query: {}", query);
        long startTime = System.currentTimeMillis();
        if (query == null || query.isBlank()) {
            throw new BadRequestException("Задан пустой поисковый запрос");
        }
        List<SiteEntity> sites = getSites(site);
        Set<String> queryLemmas = lemmaParser.parseToLemmaWithCount(query.trim()).keySet();

        Map<PageEntity, Double> pageRank = new HashMap<>();

        for (SiteEntity persistSite : sites) {
            List<LemmaEntity> sortedLemmas = queryLemmas.stream()
                    .map(lemma -> lemmaRepository.findBySiteAndLemma(persistSite, lemma))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .sorted(Comparator.comparing(LemmaEntity::getFrequency))
                    .toList();

            Set<LemmaEntity> lemmaSet = new HashSet<>(sortedLemmas);

            Set<PageEntity> pages = getPages(sortedLemmas);

            pageRank.putAll(pages.stream().collect(Collectors.toMap(Function.identity(),
                    page -> sumRank(page, lemmaSet))));
        }

        Optional<Double> optionalMaxRank = pageRank.values().stream().max(Double::compareTo);
        List<SearchData> searchData;
        if (optionalMaxRank.isEmpty()) {
            searchData = List.of();
        } else {
            double maxRank = optionalMaxRank.get();
            searchData = pageRank.entrySet().parallelStream()
                    .map(entry -> {
                        PageEntity page = entry.getKey();
                        Double rank = entry.getValue();
                        String content = page.getContent();
                        return new SearchData(page, htmlParser.getTitle(content),
                                snippetGenerator.generateSnippet(query, content),
                                (float) (rank / maxRank));
                    })
                    .sorted((a, b) -> Float.compare(b.relevance(), a.relevance()))
                    .toList();
        }
        log.info("Search time: {} ms.", System.currentTimeMillis() - startTime);
        return new SearchResponse(searchData.size(), subList(searchData, offset, limit));
    }

    private List<SearchData> subList(List<SearchData> searchData, Integer offset, Integer limit) {
        int fromIndex = offset;
        int toIndex = fromIndex + limit;

        if (toIndex > searchData.size()) {
            toIndex = searchData.size();
        }
        if (fromIndex > toIndex) {
            return List.of();
        }

        return searchData.subList(fromIndex, toIndex);
    }

    private double sumRank(PageEntity page, Set<LemmaEntity> lemmas) {
        return indexRepository.findAllByPageAndLemmaIn(page, lemmas).stream().mapToDouble(IndexEntity::getRank).sum();
    }

    private Set<PageEntity> getPages(List<LemmaEntity> sortedLemmas) {
        if (sortedLemmas.isEmpty()) return Set.of();
        Set<PageEntity> pages = sortedLemmas.get(0).getIndices().stream().map(IndexEntity::getPage).collect(Collectors.toSet());
        for (int i = 1; i < sortedLemmas.size(); i++) {
            pages = indexRepository.findAllByLemmaAndPageIn(sortedLemmas.get(i), pages).stream()
                    .map(IndexEntity::getPage)
                    .collect(Collectors.toSet());
            if (pages.isEmpty()) {
                return pages;
            }
        }
        return pages;
    }

    private List<SiteEntity> getSites(String siteUrl) {
        List<SiteEntity> sites;
        if (siteUrl == null || siteUrl.isBlank()) {
            sites = siteRepository.findAll();
        } else {
            SiteEntity site = getSite(siteUrl);
            sites = List.of(site);
        }
        checkIndexed(sites);
        return sites;
    }

    private SiteEntity getSite(String siteUrl) {
        String trimSiteUrl = siteUrl.trim();
        if (trimSiteUrl.matches(URL_REGEX)) {
            return siteRepository.findByUrlIgnoreCase(trimSiteUrl)
                    .orElseThrow(() -> new NotFoundException("Сайт не найден"));
        } else {
            throw new BadRequestException("Некорректный адрес сайта");
        }
    }

    private void checkIndexed(List<SiteEntity> sites) {
        boolean allSitesIndexed = sites.stream().allMatch(site -> site.getStatus().equals(SiteStatus.INDEXED));
        if (!allSitesIndexed) {
            throw new BadRequestException("Сайт не проиндексирован");
        }
    }
}
