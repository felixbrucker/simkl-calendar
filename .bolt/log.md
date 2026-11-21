## 2026-09-18 - Grouping Calendar Items by LocalDate Before Header String Formatting

**Learning:** Formatting calendar group headers directly inside `groupBy` (e.g., `items.groupBy { DateUtil.formatAiringDateHeader(it.date) }`) executes `DateTimeFormatter` formatting, `ChronoUnit` date arithmetic, string concatenation, and `uppercase` conversions $N$ times (once per item). By grouping by `LocalDate` first (`items.groupBy { it.date.atZone(zone).toLocalDate() }`) and then applying `mapKeys { DateUtil.formatAiringDateHeader(it.key) }`, date formatting and string allocations are reduced from $O(N)$ items to $O(D)$ unique dates (~90-95% reduction in formatting operations) while preserving chronological order via `LinkedHashMap`.

**Action:** Always group date-sorted collections by native `LocalDate` before mapping keys to localized display strings in Compose state calculations.

## 2026-09-22 - Single-Pass Segregation of Typed List Items in Compose State

**Learning:** Calling multiple sequential `.filter { ... }` predicates on a state collection in Compose screen components executes $K$ full collection traversals ($O(K \cdot N)$) and creates $K$ intermediate list and iterator allocations. Combining the segregation into a single-pass `for` loop inside `remember(items)` reduces complexity to $O(N)$ with $0$ intermediate collection allocations.

**Action:** Segregate list items by enum/type discriminator in a single pass using a `when` expression in `remember` blocks rather than chained/multiple `.filter` calls.

## 2026-09-24 - Zero-Allocation Primitive Arrays in Custom Compose Measure Policies

**Learning:** Custom Compose `MeasurePolicy` implementations execute on every layout and scroll pass. Constructing `List<Int>` via `MutableList(columns)` or `List(rows)` causes boxed `Integer` allocations for every cell dimension check alongside `ArrayList` and `Iterator` allocations during `measurables.map` and `sum()`. Replacing these with primitive `IntArray` and `Array<Placeable>` eliminates object boxing and `ArrayList` allocations on every measure pass, reducing GC pressure during list scrolling.

**Action:** Use primitive `IntArray` and `Array<Placeable>` instead of `List` or `MutableList` inside custom `MeasurePolicy.measure` blocks.

## 2026-09-25 - Index-Based Sublist Slicing & Direct Stream Writing for Log Repositories

**Learning:** Pruning chronologically ordered log entries with `entries.filter { it.timestamp >= cutoff }.takeLast(maxEntries)` performs an $O(N)$ list traversal and allocates two intermediate `ArrayList` copies. Leveraging monotonic timestamp order via `indexOfFirst` and `subList(startIndex, entries.size)` eliminates intermediate list allocations during pruning. Additionally, using `BufferedWriter` for JSON log persistence avoids allocating large `StringBuilder` heap strings containing all log lines before writing to disk.

**Action:** Use `indexOfFirst` and `subList` when pruning chronologically ordered log collections and stream JSON log files directly with `BufferedWriter`.

## 2026-09-26 - Pre-Fetching Flow Preferences Before Hot Loops in Synchronization Operations

**Learning:** Calling `preferencesFlow.first()` inside tight item-processing loops during background sync tasks triggers repeated DataStore flow collections and coroutine machinery for every individual item. Pre-fetching preferences once prior to looping and passing the pre-fetched model into resolution helpers reduces DataStore lookups from $O(N)$ to $O(1)$, avoiding redundant suspend call overhead during bulk processing.

**Action:** Pre-fetch DataStore flow preferences once before entering hot processing loops or bulk sync operations.

## 2026-09-27 - Hoisting Instant.now() Outside Batch Processing Loops in Status Resolution

**Learning:** Invoking `Instant.now()` repeatedly inside batch calendar synchronization loops over thousands of episodes incurs repeated system clock JNI overhead and `Instant` object heap allocations. Adding an optional `now: Instant = Instant.now()` parameter to resolution helpers (`MediaStatusResolver.resolve`) and passing a pre-captured `now` timestamp from the outer sync scope eliminates $N$ redundant clock queries and `Instant` heap object allocations per sync run.

**Action:** Pass pre-captured `now: Instant` timestamps into item evaluation and resolution helpers inside batch processing loops.

## 2026-09-28 - Bit-Packed Key Sorting in Torrent Search Manager (Rejected as Micro-Optimization)

**Learning:** Optimizing multi-criterion collection sorting in `TorrentSearchManager.sortedUsing` via bit-packed `Long` composite keys was rejected as a micro-optimization. Torrent search result lists are small in size, so optimizing sorting comparators on cold search execution paths offers no perceptible user-facing performance gain and adds unnecessary bit-shifting complexity.

**Action:** Avoid micro-optimizations on small collection sorting in cold execution paths (such as torrent search API responses); focus only on hot paths and measurable bottlenecks.

## 2026-09-29 - Zero-Allocation Status Aggregation Over Episode Collections

**Learning:** Computing common media status or aggregate status using `episodes.map { it.mediaStatus }.distinct()` allocates an intermediate `ArrayList` and `HashSet` for every recomposition pass and iterates over the collection multiple times. Inspecting `episodes.firstOrNull()?.mediaStatus` and evaluating `.all { it.mediaStatus == first }` or evaluating predicates (`.all`, `.any`) directly on the source collection eliminates intermediate collection allocations entirely and enables immediate short-circuiting on the first non-matching element.

**Action:** Evaluate common properties or predicates directly on source collections with `.firstOrNull()` and `.all`/`.any` short-circuiting instead of intermediate `.map { ... }.distinct()` collection transformations.

## 2026-10-01 - Hoisting Sorted Map Key Derivation in Compose Screen Components

**Learning:** Invoking `.keys.sorted()` on a grouped map (`episodes.groupBy { ... }`) directly inside a `LazyColumn` item composition scope re-executes TimSort and allocates a new `List<Int>` on every recomposition or scroll event. Moving `.keys.sorted()` into the same `remember(episodes)` block where `groupBy` is computed (`val (seasons, sortedSeasons) = remember(episodes) { ... }`) ensures keys are sorted only when source data changes, preventing redundant heap allocations during UI rendering and list scrolling.

**Action:** Always compute derived sorted map keys inside `remember` blocks alongside map grouping operations in Compose screen components.

## 2026-10-02 - Adding Indices and Leveraging Room AutoMigrations for SQL-Offloaded Sorting

**Learning:** When offloading in-memory collection sorting from ViewModel transformations to Room SQL queries (`ORDER BY`), ensure the SQL query orders by the exact matching relational property. Do not avoid adding database indices on frequently sorted or queried columns due to perceived migration complexity; Room's `AutoMigration` mechanism handles index additions seamlessly without adding manual migration code or runtime complexity.

**Action:** Add database indices on columns used for query sorting or filtering and configure Room `AutoMigration` rather than avoiding schema updates or reverting to in-memory sorting.

## 2026-10-11 - Offloading Multi-Flow State Transformations to Default Dispatcher

**Learning:** Reactive `combine` flow transformations inside ViewModels default to executing on `Dispatchers.Main` when collected by UI state flows (`stateIn`). Performing complex list transformations, string pattern matching, statistics calculation, and multi-criterion collection sorting directly on `Dispatchers.Main` causes UI thread jank and frame drops during search query typing or filter updates. Attaching `.flowOn(defaultDispatcher)` before `.stateIn(...)` offloads heavy collection computations to `Dispatchers.Default` background threads. Injecting `defaultDispatcher: CoroutineDispatcher = Dispatchers.Default` via Hilt provides clean testability without requiring static dispatcher mocking.

**Action:** Always attach `.flowOn(defaultDispatcher)` to complex `combine` or `map` flow transformations in ViewModels that process large collections or perform string matching and sorting.

## 2026-10-18 - Single-Pass Character Scanning for Anime Title Normalization (Rejected)

**Learning:** Optimizing `String.toNormalizedAnimeTitle()` with a custom single-pass character scanner was rejected as unnecessary complexity for minimal gain because title normalization is executed infrequently (only when initiating search query generation or displaying detail titles) and is not on a hot scrolling or batch processing path.

**Action:** Avoid replacing readable regex/collection string pipelines with custom character scanners unless profiling proves the string normalization is executed repeatedly in a hot path (e.g., UI rendering or batch loops).

## 2026-10-25 - Remembering Season Episode Predicates and Header State Wrappers in Compose List Components

**Learning:** Linear collection scans (`.all { ... }`, `.any { ... }`) executed directly in composables without `remember` run on every recomposition pass of child view items. Wrapping predicate calculations over child lists (`state.episodes`) in `remember(state.episodes)` and remembering parent state wrappers (`SeasonHeaderState`) avoids redundant $O(N)$ list traversals and state wrapper object allocations during recompositions.

**Action:** Always wrap collection predicate calculations (`.all`, `.any`, `.filter`) and state wrapper object instantiations in `remember` blocks when rendering group/section headers in Compose.

## 2026-11-03 - Rejection of Non-Guaranteed Unique Keys in External Search Result Lists

**Learning:** Assigning model properties like `SearchResultItem.uri` as explicit item keys in Jetpack Compose `LazyColumn` (`items(results, key = { it.uri.toString() })`) introduces crash risks (`IllegalArgumentException: Key already exists`). In multi-provider or aggregator torrent searches, different sources or mirrors may return duplicate magnet links or identical URI schemes. When an item model lacks an explicitly unique, server-backed primary identifier, falling back to Compose default index keys or ensuring deduplication upstream is required to avoid breaking UI composition.

**Action:** Never use external or third-party entity fields (such as search result download URIs) as Compose `LazyColumn` keys unless uniqueness is strictly guaranteed or enforced via prior deduplication.

## 2026-11-21 - Prohibition of Bit-Shifting or Bit-Packing Optimizations

**Learning:** Bit-shifting or bit-packing composite keys (e.g., packing `season` and `episodeNumber` integers into a `Long` using `shl` and `or`) is strictly forbidden across this codebase. It introduces unnecessary low-level complexity, reduces readability, and creates fragile code for negligible memory/CPU gains.

**Action:** NEVER use bit-shifting or bit-packing for composite keys or data structures anywhere in this project under any circumstances. Always use standard Kotlin data classes, `Pair`, or standard maps/collections instead.

## 2026-11-21 - Decoupling Transient Download Progress from Heavy Episode List State Re-Allocation

**Learning:** Including high-frequency transient state flows (such as active torrent `downloadProgress` maps updating multiple times per second) inside `combine` blocks that reconstruct and re-sort large UI collection models (`EpisodeListUiState` with seasons and episodes) causes continuous full-list re-allocation, sorting, and GC pressure during downloads. Decoupling transient progress maps from structural view state combines and observing them reactively at the row/item level avoids redundant $O(N)$ list rebuilds and prevents UI jank.

**Action:** Never include high-frequency transient progress or tick flows inside structural UI state `combine` flows; instead, expose progress flows independently and observe them directly at the component/row level.

## 2026-11-21 - Suppressing Redundant State Emissions in MediaItemDetailViewModel via DistinctUntilChanged

**Learning:** Reactive flow pipelines in `MediaItemDetailViewModel` (such as `episodesForSeasons`, `seasons`, `watchedSetFlow`, `watchProgressState`, `watchlistStatusMap`, and `EpisodeListUiState`) emitted new state values whenever upstream database updates or table triggers fired, even when the resulting domain objects and UI models were structurally identical. This triggered unnecessary downstream collection mapping, sorting, and Jetpack Compose recompositions. Inserting `.distinctUntilChanged()` after flow transformations and mapping media item relation IDs before flatMapLatest prevents redundant DAO flow re-subscriptions and UI recompositions.

**Action:** Always apply `.distinctUntilChanged()` to derived StateFlow mappings and heavy UI state collectors in ViewModels, and throttle ID extraction pipelines before flatMapLatest database queries.

## 2026-11-21 - Redundancy of DistinctUntilChanged Directly Preceding StateIn

**Learning:** Checks for equality internally in `StateFlow` and `stateIn` make `.distinctUntilChanged()` directly preceding `.stateIn` redundant when no intermediate computation or non-state-in operator (like `flatMapLatest`) intervenes.

**Action:** Avoid placing `.distinctUntilChanged()` immediately before `.stateIn`; use it only before operators like `flatMapLatest` where deduplication prevents unnecessary upstream collection restarts.
