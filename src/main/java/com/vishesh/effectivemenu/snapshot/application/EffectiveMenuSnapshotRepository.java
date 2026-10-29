package com.vishesh.effectivemenu.snapshot.application;

import java.util.Optional;

import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;

/** Port used by the application to load and save outlet snapshots. */
public interface EffectiveMenuSnapshotRepository {
    EffectiveMenuSnapshot save(EffectiveMenuSnapshot snapshot);

    Optional<EffectiveMenuSnapshot> findByOutletId(Long outletId);
}
