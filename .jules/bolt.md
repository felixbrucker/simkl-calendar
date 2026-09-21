## 2026-08-23 - Grouping Calendar Items by LocalDate Before Header String Formatting

**Learning:** Formatting calendar group headers directly inside `groupBy` (e.g., `items.groupBy { DateUtil.formatAiringDateHeader(it.date) }`) executes `DateTimeFormatter` formatting, `ChronoUnit` date arithmetic, string concatenation, and `uppercase` conversions $N$ times (once per item). By grouping by `LocalDate` first (`items.groupBy { it.date.atZone(zone).toLocalDate() }`) and then applying `mapKeys { DateUtil.formatAiringDateHeader(it.key) }`, date formatting and string allocations are reduced from $O(N)$ items to $O(D)$ unique dates (~90-95% reduction in formatting operations) while preserving chronological order via `LinkedHashMap`.

**Action:** Always group date-sorted collections by native `LocalDate` before mapping keys to localized display strings in Compose state calculations.

## 2026-08-24 - Single-Pass Segregation of Typed List Items in Compose State

**Learning:** Calling multiple sequential `.filter { ... }` predicates on a state collection in Compose screen components executes $K$ full collection traversals ($O(K \cdot N)$) and creates $K$ intermediate list and iterator allocations. Combining the segregation into a single-pass `for` loop inside `remember(items)` reduces complexity to $O(N)$ with $0$ intermediate collection allocations.

**Action:** Segregate list items by enum/type discriminator in a single pass using a `when` expression in `remember` blocks rather than chained/multiple `.filter` calls.
