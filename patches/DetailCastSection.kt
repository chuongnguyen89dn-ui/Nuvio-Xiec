package com.nuvio.app.features.details.components

// Nuvio-Xiec patch reference.
// Upstream DetailCastSection currently disables clicks when tmdbId is absent.
// Xiec needs the click event to reach the screen even without TMDB so the
// caller can choose the Xiec actor-filmography fallback.

// Replace upstream CastItem onClick assignment:
//
// onClick = if (onCastClick != null && person.tmdbId != null && person.tmdbId > 0) {
//     { onCastClick(person, sharedTransitionKey) }
// } else null
//
// with:
//
// onClick = onCastClick?.let {
//     { it(person, sharedTransitionKey) }
// }
//
// TMDB actors still carry tmdbId and continue through the native person flow.
// Non-TMDB Xiec actors are now clickable and can be routed by the caller to
// the Xiec actor catalog/filmography endpoint.
