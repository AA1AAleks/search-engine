package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.SiteEntity;
import searchengine.model.SiteStatus;

import java.util.Optional;
import java.util.Set;

public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {
    Set<SiteEntity> findAllByStatus(SiteStatus status);

    boolean existsByStatus(SiteStatus status);

    boolean existsByIdAndStatus(Integer id, SiteStatus status);

    Optional<SiteEntity> findByUrlIgnoreCase(String url);

    boolean existsByStatusNot(SiteStatus status);
}
