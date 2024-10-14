package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;

import java.util.Set;

public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {
    int countByLemma(LemmaEntity lemma);

    Set<IndexEntity> findAllByLemmaAndPageIn(LemmaEntity lemma, Set<PageEntity> pages);

    Set<IndexEntity> findAllByPageAndLemmaIn(PageEntity page, Set<LemmaEntity> lemmas);
}
