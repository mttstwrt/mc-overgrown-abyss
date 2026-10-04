# AGENTS.md: Shared contributor rules for all mods in this folder

Applies to every project under `mc-mods/` (Abyss, Rift, Sky Layer, Structure Fit, Architect's Toolbox, ...).
A project may add its own `AGENTS.md` for project-specific rules; it must not contradict this file.
Project specs (`0N-*.md`, `README.md`) say *what* to build. This file says *how*.

## 1. Guiding principles

1. **Simple beats clever.** Pick the simplest design that meets the spec. No speculative abstraction,
   no framework-building, no generic "manager" classes. Add an abstraction when there are two real
   callers, not before.
2. **Datapack first.** Code only where JSON cannot do the job. Keep tunables in datapack JSON,
   not in Java constants.
3. **Small surface area.** Every mixin, access widener/transformer, reflection call and loader hook is a
   maintenance cost on every Minecraft update. Justify each one in a comment and keep it minimal.
4. **Portability is a design constraint, not a later task.** See section 3.

## 2. Code standards

**Language and style**
- Java 21 for 1.21.x (check the pinned version in `docs/PINS.md`; do not guess). Use modern features where they
  simplify: `record`, `sealed` interfaces, pattern matching, `switch` expressions, text blocks, `var` for obvious
  types, `Optional` for return values (never fields or parameters).
- Prefer immutability: `final` fields, records for data, unmodifiable collections.
- No wildcard imports, no dead code, no commented-out code, no unused imports. Fix compiler warnings.
- Names say what a thing is. Methods are short and do one thing. Prefer early returns over nesting.
- Comments explain *why* (a Mojang quirk, a version workaround), never *what*. Match the density of surrounding code.
- Use Mojang official mappings everywhere. Do not mix mapping sets in shared code.
- Logging via SLF4J (`LogUtils.getLogger()`), never `System.out`.

**Object-oriented design**
- Single responsibility per class; depend on interfaces at boundaries (platform, config), concrete types inside.
- Composition over inheritance. Inherit only from Minecraft types you are required to extend.
- Encapsulate: private fields, narrow public API, package-private by default.
- Make illegal states unrepresentable (records with validating compact constructors, enums, sealed types).
- Dependency injection by constructor or parameter. No global mutable state; no singletons except the
  platform-service lookup in section 3.
- Define worldgen data types as records with a `MapCodec`/`Codec`. The codec *is* the schema: keep it in
  one place next to the record.

**Worldgen specifics**
- **Thread safety:** chunk generation is multithreaded. Anything reachable from worldgen must be immutable or
  safely concurrent. No unsynchronised caches, no `static` mutable fields, no world/level access that is not
  a worldgen-safe accessor.
- **Determinism:** all randomness derives from the world seed and coordinates (plus the project's salt).
  Never `new Random()`, `Math.random()` or time.
- Do not override vanilla terrain files (`noise_settings`, density functions, `dimension_type`); wrap or patch
  at runtime. Vanilla blocks only unless the spec says otherwise.
- Fresh worlds only. Servers need no client-side install unless the spec says so.

**Testing and verification**
- Pure logic (math, noise functions, scoring, codecs) gets JUnit tests that need no Minecraft bootstrap
  where possible.
- Every phase in a spec has a gate; do not call a phase done until its gate is demonstrated (screenshots,
  fixed-seed world, `/locate`, dedicated-server boot). Say plainly when something is untested.
- Always verify on a **dedicated server** as well as the client, and on **both loaders**.

## 3. Cross-version and cross-loader portability

**Target matrix:** NeoForge and Fabric, Minecraft 1.21.1 first, newer versions later. Treat every Minecraft
bump as expected.

**Module layout** (same as `Architects_Toolbox`):
```
common/    all game logic, codecs, worldgen types, shared mixins, resources (datapack JSON)
fabric/    entrypoint + loader glue only
neoforge/  entrypoint + loader glue only
```
- Platform modules are thin: target ~100 lines or fewer each. If logic is creeping in, move it to `common`.
- `common` must **never** import `net.fabricmc.*` or `net.neoforged.*`. Enforce this in the build
  (compile `common` without loader dependencies, so a violation fails to compile).
- Reach loader features through a small interface in `common` (for example `Platform`, `RegistryHelper`,
  `BiomeInjector`) with one implementation per loader, found via `ServiceLoader`. Keep these interfaces
  tiny and purpose-named, not a mirror of a loader API.
- Shared mixins live in `common` (Mixin is the same on both loaders). Loader-specific mixins are a last resort.
- Data differences between loaders belong in the loader module's resources, not in conditionals:
  - NeoForge biome modifiers: `data/<ns>/neoforge/biome_modifier/`. Fabric has no datapack equivalent, so use
    `BiomeModifications` in code, driven by the same placed-feature IDs.
  - Use tags that exist on both loaders (`c:` convention tags, or your own namespaced tags). Do not rely on
    loader-only tags.
  - Conditional loading goes through each loader's mechanism in its own module.
- Registries: register through the platform layer; never hold direct references to registry objects before
  registration completes.

**Version-sensitive code** (anything Mojang renames or reformats between releases):
- Isolate it. Put code that touches unstable Minecraft internals (`NoiseChunk`, `Aquifer`/fluid picker, `Structure`
  placement internals, rendering) in one clearly named package such as `…compat` or `…mc`, wrapped by a small
  project-owned interface. Business logic must call the interface, not the internals.
- Datapack JSON formats change between versions (folder names, field names, registry formats). Keep the version
  in `docs/PINS.md` and note which files are version-specific.
- When a Minecraft update breaks something, fix it in the compat layer. Do not scatter version `if`s through logic.
- When a second Minecraft version is actually supported, adopt a multi-version tool (see the library notes in
  each project's scope doc) rather than copying branches or source trees. Do not adopt it before then.
- Pin exact versions of Minecraft, loaders, mappings, Java and every library in `gradle.properties` plus
  `docs/PINS.md`. No `+` or `latest` ranges.

**Dependencies**
- **Allowed:** widely used common libraries and APIs (for example Architectury API, Fabric API, Cloth Config,
  worldgen helper libraries such as Lithostitched) when they remove real loader glue or boilerplate. Prefer one
  well-maintained library over hand-rolled platform code, and prefer fewer libraries over more.
- **Forbidden as hard dependencies:** terrain, biome, or content mods (for example Larion, TerraBlender-based
  biome packs, Biomes O' Plenty) and sibling projects. These may be *test targets* and may be supported
  through optional, soft integration, but the mod must load and work without them.
- Record each library (purpose, version, why it was chosen over writing the code) in `docs/PINS.md`.
  `common` may use a library only if it is available on both loaders for the pinned version.
- Build-time-only tools (Gradle plugins, JUnit) are fine.
- Shared code between projects (for example `ravine-core`) is shared by **source inclusion**, not by a
  runtime mod dependency.

## 4. Working agreements

- Read the project spec and `docs/PINS.md` before changing code. If the spec and these rules conflict, stop and
  ask; do not silently pick.
- Items marked `[recall; verify]` in specs are unverified memory. Verify against the pinned version's real
  files (use the game's data generator and decompiled sources) before building on them.
- Keep changes small and reviewable. One concern per commit. Do not commit generated output, run directories
  or build products.
- Do not invent APIs. If a class or method is not found in the pinned version, look it up; do not assume.
- Report honestly: say what was run, what passed, what was not tested.
