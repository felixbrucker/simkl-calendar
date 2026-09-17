## 2026-08-23 - Grouping Calendar Items by LocalDate Before Header String Formatting

**Learning:** Formatting calendar group headers directly inside `groupBy` (e.g., `items.groupBy { DateUtil.formatAiringDateHeader(it.date) }`) executes `DateTimeFormatter` formatting, `ChronoUnit` date arithmetic, string concatenation, and `uppercase` conversions $N$ times (once per item). By grouping by `LocalDate` first (`items.groupBy { it.date.atZone(zone).toLocalDate() }`) and then applying `mapKeys { DateUtil.formatAiringDateHeader(it.key) }`, date formatting and string allocations are reduced from $O(N)$ items to $O(D)$ unique dates (~90-95% reduction in formatting operations) while preserving chronological order via `LinkedHashMap`.

**Action:** Always group date-sorted collections by native `LocalDate` before mapping keys to localized display strings in Compose state calculations.
