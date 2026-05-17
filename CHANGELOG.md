# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project follows [Semantic Versioning](https://semver.org/). The two
packages — `@openide/reatom-ts-plugin` and the IDE plugin — are versioned
together.

## [0.0.1] — Unreleased

Initial pre-release; early development.

### Added

- **Reactive graph analyzer** (`@openide/reatom-ts-plugin`) — parses a
  TypeScript project on the TypeScript Compiler API and builds a JSON model of
  the Reatom reactive graph: `atom` / `computed` / `action` / `effect` nodes
  and `read` / `write` / `extend` edges.
- **IDE plugin — Code Lens**: a summary line above each unit declaration with
  the unit's role and reactive-link counters.
- **IDE plugin — gutter icons**: separate reader and writer icons on
  declarations (a click opens a popup with the usages), and a
  jump-to-declaration icon on usage lines.
- **IDE plugin — navigation** "usage ↔ declaration", including across files.
- The IDE plugin ships the analyzer bundled inside itself — a consumer project
  only needs a `@reatom/core` dependency, no extra npm package.
