package com.vishesh.effectivemenu.snapshot.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vishesh.effectivemenu.snapshot.application.EffectiveMenuSnapshotService;
import com.vishesh.effectivemenu.snapshot.domain.EffectiveMenuSnapshot;

/** Customer-facing endpoint for reading one outlet's effective menu. */
@RestController
@RequestMapping("/api/v1/outlets")
public class EffectiveMenuSnapshotController {
    private final EffectiveMenuSnapshotService service;

    public EffectiveMenuSnapshotController(EffectiveMenuSnapshotService service) {
        this.service = service;
    }

    @GetMapping("/{outletId}/menu")
    public ResponseEntity<EffectiveMenuSnapshotResponse> getEffectiveMenuSnapshot(@PathVariable Long outletId) {
        EffectiveMenuSnapshot snapshot = service.getCustomerSnapshot(outletId);
        var response = new EffectiveMenuSnapshotResponse(
                snapshot.getOutletId(),
                snapshot.getSnapshotVersion(),
                snapshot.getEffectiveMenu(),
                snapshot.getCalculatedAt(),
                snapshot.getUpdatedAt());

        return ResponseEntity.ok(response);
    } 

}
