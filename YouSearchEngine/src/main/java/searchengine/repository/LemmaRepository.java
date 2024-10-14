package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.Optional;
import java.util.Set;

public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {
    Optional<LemmaEntity> findBySiteAndLemma(SiteEntity site, String lemma);

    Set<LemmaEntity> findAllBySite(SiteEntity site);

    long countBySite(SiteEntity site);
}
