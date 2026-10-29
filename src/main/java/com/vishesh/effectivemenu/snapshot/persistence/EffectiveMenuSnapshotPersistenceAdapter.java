package com.vishesh.effectivemenu.snapshot.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.vishesh.effectivemenu.snapshot.application.EffectiveMenuSnapshotRepository;
import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;

@Repository
/** Converts snapshot domain objects to database rows and back. */
public class EffectiveMenuSnapshotPersistenceAdapter implements EffectiveMenuSnapshotRepository {

    private final EffectiveMenuSnapshotJpaRepository repository;

    public EffectiveMenuSnapshotPersistenceAdapter(EffectiveMenuSnapshotJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public EffectiveMenuSnapshot save(EffectiveMenuSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Snapshot must not be null");
        }

        EffectiveMenuSnapshotEntity savedEntity = repository.save(toEntity(snapshot));

        return toDomain(savedEntity);
    }

    @Override
    public Optional<EffectiveMenuSnapshot> findByOutletId(Long outletId) {
        if (outletId == null || outletId <= 0) {
            throw new IllegalArgumentException("Outlet ID must be positive");
        }
        return repository.findById(outletId).map(this::toDomain);

    }

    private EffectiveMenuSnapshotEntity toEntity(EffectiveMenuSnapshot domain) {

        if (domain == null) {
            throw new IllegalArgumentException("Snapshot must not be null");
        }

        return new EffectiveMenuSnapshotEntity(
                domain.getOutletId(),
                domain.getSnapshotVersion(),
                domain.getEffectiveMenu(),
                domain.getCalculatedAt(),
                domain.getUpdatedAt());
    }

    private EffectiveMenuSnapshot toDomain(EffectiveMenuSnapshotEntity entity) {

        if (entity == null) {
            throw new IllegalArgumentException("Snapshot entity must not be null");
        }

        EffectiveMenuSnapshot domain = new EffectiveMenuSnapshot(entity.getOutletId(),
                entity.getSnapshotVersion(),
                entity.getEffectiveMenu(),
                entity.getCalculatedAt(),
                entity.getUpdatedAt());

        return domain;
    }

}
