package com.vishesh.effectivemenu.snapshot.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for snapshot rows. */
public interface EffectiveMenuSnapshotJpaRepository extends JpaRepository<EffectiveMenuSnapshotEntity, Long> {
}
