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
