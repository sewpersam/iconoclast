package dev.iconoclast.app.coverage

/** A launchable app's status on the coverage screen, spec 2.2 screen 5. */
enum class CoverageStatus {
    /** Arcticons has its drawable and the theme draws it as the theme says. */
    THEMED,

    /** Arcticons has its drawable and the active theme overrides it. */
    OVERRIDDEN,

    /** No Arcticons drawable: it keeps its system icon; the screen links the request process. */
    NOT_IN_ARCTICONS,
}

/** One launchable component, `package/class`, its drawable name when covered, and its status. */
data class CoverageEntry(val component: String, val drawable: String?, val status: CoverageStatus)

/**
 * The coverage diff, run when the app opens, spec 2.5: installed apps now against the
 * last successful Apply.
 *
 * @property entries every installed launchable component, sorted, with its status.
 * @property installedSinceApply components installed since the last Apply, sorted; empty
 *   when nothing was ever applied. Non-empty is the badge.
 * @property reapplyWouldTheme those of them Arcticons covers: what a re-apply adds.
 * @property notCovered components with no Arcticons drawable, sorted.
 * @property lastApply the last Apply, or null when there was none.
 */
class CoverageReport(
    val entries: List<CoverageEntry>,
    val installedSinceApply: List<String>,
    val reapplyWouldTheme: List<String>,
    val notCovered: List<String>,
    val lastApply: AppliedCoverage?,
) {
    companion object {
        /**
         * [installed] is InstalledApps.launchableComponents. [drawableOf] resolves a component
         * through the appfilter to its drawable name, or null; calendar entries resolve to
         * their static drawable, spec 2.5, because the compiled appfilter keeps only `item`
         * entries. [overridden] is the active document's override keys.
         */
        fun of(
            installed: Collection<String>,
            last: AppliedCoverage?,
            drawableOf: (String) -> String?,
            overridden: Set<String>,
        ): CoverageReport {
            val entries = installed.toSortedSet().map { c ->
                val d = drawableOf(c)
                val status = when {
                    d == null -> CoverageStatus.NOT_IN_ARCTICONS
                    d in overridden -> CoverageStatus.OVERRIDDEN
                    else -> CoverageStatus.THEMED
                }
                CoverageEntry(c, d, status)
            }
            val since = if (last == null) emptyList() else entries.filter { it.component !in last.components }
            return CoverageReport(
                entries,
                since.map { it.component },
                since.filter { it.drawable != null }.map { it.component },
                entries.filter { it.drawable == null }.map { it.component },
                last,
            )
        }
    }
}
