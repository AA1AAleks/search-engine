package searchengine.service.indexing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.UnsupportedMimeTypeException;
import searchengine.dto.PageInfo;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.SiteStatus;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.service.lemma.LemmaService;
import searchengine.util.HtmlParser;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class UrlParser extends RecursiveAction {
    private final Integer siteId;
    private final String path;
    private final transient SiteRepository siteRepository;
    private final transient PageRepository pageRepository;
    private final transient LemmaService lemmaService;
    private final transient HtmlParser htmlParser;
    private final boolean isFirstAction;

    @Override
    protected void compute() {
        if (isNotFailed(siteId) && isNotVisited(siteId, path)) {
            try {
                updateStatusTime(siteId);
                Optional<PageEntity> optionalPage = savePage(siteId, path);

                if (optionalPage.isPresent()) {
                    PageEntity page = optionalPage.get();

                    if (page.getCode() < 400) {
                        lemmaService.findAndSave(page);
                    }

                    Set<ForkJoinTask<Void>> tasks = htmlParser.getPaths(page.getContent()).stream()
                            .map(pathFromPage -> new UrlParser(siteId, pathFromPage,
                                    siteRepository, pageRepository,
                                    lemmaService,
                                    htmlParser,
                                    false).fork())
                            .collect(Collectors.toSet());
                    tasks.forEach(ForkJoinTask::join);

                    if (isFirstAction && isNotFailed(siteId)) {
                        lemmaService.updateLemmasFrequency(siteId);
                        indexed(siteId);
                    }
                }
            } catch (UnsupportedMimeTypeException e) {
                log.warn("UnsupportedMimeTypeException", e);
            } catch (InterruptedException e){
                log.warn("Interrupted!", e);
                failed(siteId, "--> Ошибка парсинг: Interrupted");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Parser exception", e);
                failed(siteId, "--> Ошибка парсинг URL: " + getPersistSite(siteId).getUrl() + path);
            }
        }
    }

    private boolean isNotVisited(Integer siteId, String path) {
        return !pageRepository.existsBySiteIdAndPath(siteId, path);
    }

    public Optional<PageEntity> savePage(Integer siteId, String path) throws IOException, InterruptedException {
        synchronized (pageRepository) {
            SiteEntity site = getPersistSite(siteId);
            PageInfo pageInfo = htmlParser.getPageInfo(site.getUrl() + path);
            if (isNotVisited(siteId, path)) {
                return Optional.of(pageRepository.save(PageEntity.builder()
                        .path(path)
                        .site(site)
                        .code(pageInfo.getStatusCode())
                        .content(pageInfo.getContent())
                        .build()));
            } else {
                return Optional.empty();
            }
        }
    }

    private void failed(Integer siteId, String error) {
        log.warn("Failed indexing site with id {}: {}", siteId, error);
        SiteEntity persistSite = getPersistSite(siteId);
        persistSite.setLastError(error);
        persistSite.setStatus(SiteStatus.FAILED);
        siteRepository.save(persistSite);
    }


    private void updateStatusTime(Integer siteId) {
        SiteEntity persistSite = getPersistSite(siteId);
        persistSite.setStatusTime(LocalDateTime.now());
        siteRepository.save(persistSite);
    }

    private void indexed(Integer siteId) {
        SiteEntity persistSite = getPersistSite(siteId);
        persistSite.setStatusTime(LocalDateTime.now());
        persistSite.setStatus(SiteStatus.INDEXED);
        siteRepository.save(persistSite);
    }

    private SiteEntity getPersistSite(Integer siteId) {
        return siteRepository.findById(siteId)
                .orElseThrow(() -> new IllegalStateException("Site not found"));
    }

    private boolean isNotFailed(Integer siteId) {
        return !siteRepository.existsByIdAndStatus(siteId, SiteStatus.FAILED);
    }
}
