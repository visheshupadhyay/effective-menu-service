package com.vishesh.effectivemenu.snapshot.api;

/**
 * ApiError
 */
/** Small, consistent error body returned by the REST API. */
public record ApiError(int status, String message, String path) {
}
