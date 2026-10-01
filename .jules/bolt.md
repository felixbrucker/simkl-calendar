## 2026-08-23 - Grouping Calendar Items by LocalDate Before Header String Formatting

**Learning:** Formatting calendar group headers directly inside `groupBy` (e.g., `items.groupBy { DateUtil.formatAiringDateHeader(it.date) }`) executes `DateTimeFormatter` formatting, `ChronoUnit` date arithmetic, string concatenation, and `uppercase` conversions $N$ times (once per item). By grouping by `LocalDate` first (`items.groupBy { it.date.atZone(zone).toLocalDate() }`) and then applying `mapKeys { DateUtil.formatAiringDateHeader(it.key) }`, date formatting and string allocations are reduced from $O(N)$ items to $O(D)$ unique dates (~90-95% reduction in formatting operations) while preserving chronological order via `LinkedHashMap`.

**Action:** Always group date-sorted collections by native `LocalDate` before mapping keys to localized display strings in Compose state calculations.

## 2026-08-24 - Single-Pass Segregation of Typed List Items in Compose State

**Learning:** Calling multiple sequential `.filter { ... }` predicates on a state collection in Compose screen components executes $K$ full collection traversals ($O(K \cdot N)$) and creates $K$ intermediate list and iterator allocations. Combining the segregation into a single-pass `for` loop inside `remember(items)` reduces complexity to $O(N)$ with $0$ intermediate collection allocations.

**Action:** Segregate list items by enum/type discriminator in a single pass using a `when` expression in `remember` blocks rather than chained/multiple `.filter` calls.

## 2026-09-23 - Zero-Allocation Primitive Arrays in Custom Compose Measure Policies

**Learning:** Custom Compose `MeasurePolicy` implementations execute on every layout and scroll pass. Constructing `List<Int>` via `MutableList(columns)` or `List(rows)` causes boxed `Integer` allocations for every cell dimension check alongside `ArrayList` and `Iterator` allocations during `measurables.map` and `sum()`. Replacing these with primitive `IntArray` and `Array<Placeable>` eliminates object boxing and `ArrayList` allocations on every measure pass, reducing GC pressure during list scrolling.

**Action:** Use primitive `IntArray` and `Array<Placeable>` instead of `List` or `MutableList` inside custom `MeasurePolicy.measure` blocks.

## 2026-10-15 - Index-Based Sublist Slicing & Direct Stream Writing for Log Repositories

**Learning:** Pruning chronologically ordered log entries with `entries.filter { it.timestamp >= cutoff }.takeLast(maxEntries)` performs an $O(N)$ list traversal and allocates two intermediate `ArrayList` copies. Leveraging monotonic timestamp order via `indexOfFirst` and `subList(startIndex, entries.size)` eliminates intermediate list allocations during pruning. Additionally, using `BufferedWriter` for JSON log persistence avoids allocating large `StringBuilder` heap strings containing all log lines before writing to disk.

**Action:** Use `indexOfFirst` and `subList` when pruning chronologically ordered log collections and stream JSON log files directly with `BufferedWriter`.

## 2026-10-28 - Pre-Fetching Flow Preferences Before Hot Loops in Synchronization Operations

**Learning:** Calling `preferencesFlow.first()` inside tight item-processing loops during background sync tasks triggers repeated DataStore flow collections and coroutine machinery for every individual item. Pre-fetching preferences once prior to looping and passing the pre-fetched model into resolution helpers reduces DataStore lookups from $O(N)$ to $O(1)$, avoiding redundant suspend call overhead during bulk processing.

**Action:** Pre-fetch DataStore flow preferences once before entering hot processing loops or bulk sync operations.

## 2026-11-12 - Hoisting Instant.now() Outside Batch Processing Loops in Status Resolution

**Learning:** Invoking `Instant.now()` repeatedly inside batch calendar synchronization loops over thousands of episodes incurs repeated system clock JNI overhead and `Instant` object heap allocations. Adding an optional `now: Instant = Instant.now()` parameter to resolution helpers (`MediaStatusResolver.resolve`) and passing a pre-captured `now` timestamp from the outer sync scope eliminates $N$ redundant clock queries and `Instant` heap object allocations per sync run.

**Action:** Pass pre-captured `now: Instant` timestamps into item evaluation and resolution helpers inside batch processing loops.

## 2026-11-25 - Bit-Packed Key Sorting in Torrent Search Manager (Rejected as Micro-Optimization)

**Learning:** Optimizing multi-criterion collection sorting in `TorrentSearchManager.sortedUsing` via bit-packed `Long` composite keys was rejected as a micro-optimization. Torrent search result lists are small in size, so optimizing sorting comparators on cold search execution paths offers no perceptible user-facing performance gain and adds unnecessary bit-shifting complexity.

**Action:** Avoid micro-optimizations on small collection sorting in cold execution paths (such as torrent search API responses); focus only on hot paths and measurable bottlenecks.

## 2026-12-14 - Zero-Allocation Status Aggregation Over Episode Collections

**Learning:** Computing common media status or aggregate status using `episodes.map { it.mediaStatus }.distinct()` allocates an intermediate `ArrayList` and `HashSet` for every recomposition pass and iterates over the collection multiple times. Inspecting `episodes.firstOrNull()?.mediaStatus` and evaluating `.all { it.mediaStatus == first }` or evaluating predicates (`.all`, `.any`) directly on the source collection eliminates intermediate collection allocations entirely and enables immediate short-circuiting on the first non-matching element.

**Action:** Evaluate common properties or predicates directly on source collections with `.firstOrNull()` and `.all`/`.any` short-circuiting instead of intermediate `.map { ... }.distinct()` collection transformations.

## 2027-01-18 - Hoisting Sorted Map Key Derivation in Compose Screen Components

**Learning:** Invoking `.keys.sorted()` on a grouped map (`episodes.groupBy { ... }`) directly inside a `LazyColumn` item composition scope re-executes TimSort and allocates a new `List<Int>` on every recomposition or scroll event. Moving `.keys.sorted()` into the same `remember(episodes)` block where `groupBy` is computed (`val (seasons, sortedSeasons) = remember(episodes) { ... }`) ensures keys are sorted only when source data changes, preventing redundant heap allocations during UI rendering and list scrolling.

**Action:** Always compute derived sorted map keys inside `remember` blocks alongside map grouping operations in Compose screen components.

## 2027-02-10 - Adding Indices and Leveraging Room AutoMigrations for SQL-Offloaded Sorting

**Learning:** When offloading in-memory collection sorting from ViewModel transformations to Room SQL queries (`ORDER BY`), ensure the SQL query orders by the exact matching relational property. Do not avoid adding database indices on frequently sorted or queried columns due to perceived migration complexity; Room's `AutoMigration` mechanism handles index additions seamlessly without adding manual migration code or runtime complexity.

**Action:** Add database indices on columns used for query sorting or filtering and configure Room `AutoMigration` rather than avoiding schema updates or reverting to in-memory sorting.
