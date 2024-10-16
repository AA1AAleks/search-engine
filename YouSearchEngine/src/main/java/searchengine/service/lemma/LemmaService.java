package searchengine.service.lemma;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.SiteRepository;
import searchengine.util.HtmlParser;
import searchengine.util.LemmaParser;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class LemmaService {
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private final SiteRepository siteRepository;
    private final LemmaParser lemmaParser;
    private final HtmlParser pageService;

    public void findAndSave(PageEntity page) {
        String text = pageService.htmlToText(page.getContent());
        Map<String, Long> lemmas = lemmaParser.parseToLemmaWithCount(text);
        Set<LemmaEntity> lemmaSetToSave = new HashSet<>();
        Set<IndexEntity> indices = new HashSet<>();
        synchronized (lemmaRepository) {
            lemmas.forEach((name, count) -> {
                Optional<LemmaEntity> optionalLemma = lemmaRepository.findBySiteAndLemma(page.getSite(), name);
                LemmaEntity lemma;
                if (optionalLemma.isPresent()) {
                    lemma = optionalLemma.get();
                } else {
                    lemma = LemmaEntity.builder()
                            .frequency(0)
                            .lemma(name)
                            .site(page.getSite())
                            .build();
                    lemmaSetToSave.add(lemma);
                }

                indices.add(IndexEntity.builder()
                        .page(page)
                        .lemma(lemma)
                        .rank((float) count)
                        .build());
            });
            lemmaRepository.saveAll(lemmaSetToSave);
        }
        indexRepository.saveAll(indices);
    }

    public void updateLemmasFrequency(Integer siteId) {
        SiteEntity site = siteRepository.findById(siteId).orElseThrow(() -> new IllegalStateException("Site not found"));
        Set<LemmaEntity> lemmaToSave = new HashSet<>();
        Set<LemmaEntity> lemmaToDelete = new HashSet<>();
        log.info("Start calculate lemmas frequency for site: {}", site);
        for (LemmaEntity lemma : lemmaRepository.findAllBySite(site)) {
            int frequency = indexRepository.countByLemma(lemma);
            if (frequency == 0) {
                lemmaToDelete.add(lemma);
            } else if (lemma.getFrequency() != frequency) {
                lemma.setFrequency(frequency);
                lemmaToSave.add(lemma);
            }
        }
        log.info("Delete old lemmas: " + lemmaToDelete.size());
        lemmaRepository.deleteAll(lemmaToDelete);
        log.info("Update lemmas: " + lemmaToSave.size());
        lemmaRepository.saveAll(lemmaToSave);
    }
}
