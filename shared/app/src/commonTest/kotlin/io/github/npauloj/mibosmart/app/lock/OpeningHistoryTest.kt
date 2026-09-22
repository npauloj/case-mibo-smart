package io.github.npauloj.mibosmart.app.lock

import io.github.npauloj.mibosmart.domain.error.SmartHomeException
import io.github.npauloj.mibosmart.domain.lock.OpeningKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

/**
 * SPEC L9, L10 and U4: what the opening history says, and what it costs.
 *
 * The rules under test are all about not losing information — an opening the app cannot classify is
 * still an opening, an entry with nobody's name still has to read as something, and a time is shown
 * twice because one way of saying it is never enough.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpeningHistoryTest {

    /**
     * SPEC L9: the two `tipo` values the partner was ever observed sending, kept apart.
     *
     * `usuarioRemoto` and `interno` mean different things to whoever reads the list — somebody
     * opened the door from an app, or somebody was standing at it — so they may never collapse into
     * one another. The wire words themselves stop in `:shared:data`; what arrives here is the
     * distinction.
     */
    @Test
    fun mapsKnownTypes() = runTest {
        val viewModel = openingHistoryViewModel(
            FakeLockRepository(
                history = listOf(
                    LockSamples.opening(minutesAgo = 5, kind = OpeningKind.Remote, actor = "Ana"),
                    LockSamples.opening(minutesAgo = 90, kind = OpeningKind.Local),
                ),
            ),
        )

        viewModel.onOpen(LockSamples.Address)

        val rows = viewModel.entries()
        assertEquals(listOf(OpeningKind.Remote, OpeningKind.Local), rows.map { it.kind })
        assertEquals(null, rows.last().actor, "an opening at the door itself names nobody")
    }

    /**
     * SPEC L9's `[ASSUMED]`, made a rule: a `tipo` nobody has seen reaches the screen as it came.
     *
     * The list of types was never published — only two were observed — so a filter here would drop
     * exactly the openings worth looking at: the ones this app cannot explain. The raw word is
     * carried through and the entry keeps its place in the list.
     */
    @Test
    fun unknownTypeShownRaw() = runTest {
        val viewModel = openingHistoryViewModel(
            FakeLockRepository(
                history = listOf(
                    LockSamples.opening(minutesAgo = 1, kind = OpeningKind.Unknown("biometria")),
                    LockSamples.opening(minutesAgo = 2, kind = OpeningKind.Remote, actor = "Ana"),
                ),
            ),
        )

        viewModel.onOpen(LockSamples.Address)

        val rows = viewModel.entries()
        assertEquals(2, rows.size, "an unrecognised opening may never be dropped")
        assertEquals(OpeningKind.Unknown("biometria"), rows.first().kind)
    }

    /**
     * ADR-007: the partner's catalogue names an opening this app has no word of its own for.
     *
     * The raw `tipo` stays on the row beside the label — it is what the partner said, and the two
     * are not the same claim — so nothing is lost by a catalogue that turns out to be wrong. A code
     * the catalogue has never heard of keeps reading exactly as [unknownTypeShownRaw] asserts, which
     * is also what the whole of iOS does.
     */
    @Test
    fun catalogNamesAnUnknownType() = runTest {
        val viewModel = openingHistoryViewModel(
            FakeLockRepository(
                history = listOf(
                    LockSamples.opening(minutesAgo = 1, kind = OpeningKind.Unknown("biometria")),
                    LockSamples.opening(minutesAgo = 2, kind = OpeningKind.Unknown("teclado")),
                    LockSamples.opening(minutesAgo = 3, kind = OpeningKind.Local),
                ),
            ),
            catalog = FakeModelCatalog("biometria" to "Abertura por biometria"),
        )

        viewModel.onOpen(LockSamples.Address)

        val rows = viewModel.entries()
        assertEquals("Abertura por biometria", rows[0].catalogLabel)
        assertEquals(OpeningKind.Unknown("biometria"), rows[0].kind, "the partner's word is not overwritten")
        assertEquals("teclado", rows[1].catalogLabel, "a code nobody catalogued stays the code")
        assertEquals(null, rows[2].catalogLabel, "an opening the app names itself is not the catalogue's business")
    }

    /** SPEC L10: the partner answered and the door has not been opened — an answer, not an error. */
    @Test
    fun emptyState() = runTest {
        val viewModel = openingHistoryViewModel(FakeLockRepository(history = emptyList()))

        viewModel.onOpen(LockSamples.Address)

        val state = assertIs<OpeningHistoryUiState.Entries>(
            viewModel.state.value,
            "an empty history is not a failure",
        )
        assertTrue(state.isEmpty, "the screen has nothing to say \"Sem aberturas registradas\" about")
    }

    /**
     * SPEC U4: both times, on a clock that does not move.
     *
     * "há 5 min" on its own is unanchored and "21/09/2026 11:55" on its own has to be subtracted in
     * the reader's head. The absolute half is also the day-first order a Brazilian reader expects —
     * the one thing a machine locale would silently get wrong.
     */
    @Test
    fun entryShowsRelativeAndAbsoluteTime() = runTest {
        val viewModel = openingHistoryViewModel(
            FakeLockRepository(history = listOf(LockSamples.opening(5, OpeningKind.Local))),
        )

        viewModel.onOpen(LockSamples.Address)

        val row = viewModel.entries().single()
        assertEquals(LastSeen.Minutes(5), row.age)
        assertEquals("21/09/2026 11:55", row.absoluteTime)
    }

    /**
     * SPEC U4: a remote opening carries who did it, and one without a name is still not blank.
     *
     * The name is the whole point of the criterion — "quem abriu a porta" is what the partner's own
     * users could not see — and it is also personal data: it lives in this state while the screen is
     * up and is never logged.
     */
    @Test
    fun remoteEntryShowsActorName() = runTest {
        val viewModel = openingHistoryViewModel(
            FakeLockRepository(
                history = listOf(
                    LockSamples.opening(1, OpeningKind.Remote, actor = "Ana"),
                    LockSamples.opening(2, OpeningKind.Remote),
                ),
            ),
        )

        viewModel.onOpen(LockSamples.Address)

        val rows = viewModel.entries()
        assertEquals("Ana", rows.first().actor)
        assertEquals(
            OpeningKind.Remote,
            rows.last().kind,
            "an unnamed remote opening is still a remote opening, not an unknown one",
        )
    }

    /**
     * SPEC L9: newest first, decided here rather than trusted from the partner.
     *
     * The fake answers in the order it was given, which is the order the endpoint happens to use on
     * a good day. Nothing documents it, so the list is sorted where the rule belongs.
     */
    @Test
    fun newestFirst() = runTest {
        val viewModel = openingHistoryViewModel(
            FakeLockRepository(
                history = listOf(
                    LockSamples.opening(minutesAgo = 4_000, kind = OpeningKind.Local),
                    LockSamples.opening(minutesAgo = 5, kind = OpeningKind.Local),
                    LockSamples.opening(minutesAgo = 180, kind = OpeningKind.Local),
                ),
            ),
        )

        viewModel.onOpen(LockSamples.Address)

        assertEquals(
            listOf(LastSeen.Minutes(5), LastSeen.Hours(3), LastSeen.Days(2)),
            viewModel.entries().map { it.age },
        )
    }

    /**
     * SPEC L9 and ADR-006: one request to enter the tab, none to come back to it.
     *
     * The endpoint is not paginated, so 50 entries is the whole answer and a second call would buy
     * nothing — while the account pays for it. Switching tabs is the common case, and it must be
     * free.
     */
    @Test
    fun oneRequestPerTabEntry() = runTest {
        val repository = FakeLockRepository(history = listOf(LockSamples.opening(5, OpeningKind.Local)))
        val viewModel = openingHistoryViewModel(repository)

        viewModel.onOpen(LockSamples.Address)
        viewModel.onOpen(LockSamples.Address)

        assertEquals(
            listOf(FakeLockRepository.Read.HISTORY),
            repository.reads.map { it.endpoint },
            "returning to the history tab must not spend a second request",
        )
        assertEquals(listOf(OpeningHistory.ENTRIES), repository.requestedEntries)
    }

    /**
     * SPEC U6: a history that could not be read says why and offers the one action that helps.
     *
     * "Tentar novamente" is a tap and never a timer (SPEC E5) — and it is the only thing besides
     * entering the tab that spends a request here.
     */
    @Test
    fun aFailedReadIsNamedAndRetryable() = runTest {
        val repository = FakeLockRepository(
            answer = { throw SmartHomeException.Offline(null) },
            history = listOf(LockSamples.opening(5, OpeningKind.Local)),
        )
        val viewModel = openingHistoryViewModel(repository)

        viewModel.onOpen(LockSamples.Address)

        val failed = assertIs<OpeningHistoryUiState.Failed>(viewModel.state.value)
        assertEquals(LockError.Offline, failed.error)

        viewModel.onRetry()

        assertEquals(2, repository.reads.size, "retry is what asks the partner again — and only it")
    }

    private fun OpeningHistoryViewModel.entries(): List<OpeningRow> =
        assertIs<OpeningHistoryUiState.Entries>(state.value).rows
}
