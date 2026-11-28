package cz.olda.tasks.ui.upcoming

import cz.olda.tasks.data.SettingsRepository
import cz.olda.tasks.data.TaskRepository
import cz.olda.tasks.ui.today.FakeSettingDao
import cz.olda.tasks.ui.today.FakeTaskDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class UpcomingViewModelTest {
    @Before fun setMain() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun resetMain() = Dispatchers.resetMain()

    private fun viewModel(dao: FakeSettingDao = FakeSettingDao()) = UpcomingViewModel(
        TaskRepository(FakeTaskDao(emptyList()), Clock.systemDefaultZone()),
        SettingsRepository(dao),
    )

    @Test fun isNullUntilTheFirstEmissionThenTheLoadedList() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        assertNull(vm.state.value.days) // not loaded: must not look like "nothing coming up"
        runCurrent()
        assertEquals(emptyList<Any>(), vm.state.value.days)
    }

    @Test fun theRememberedChoiceIsWhatTheScreenStartsWith() = runTest {
        val dao = FakeSettingDao(mutableMapOf(SettingsRepository.HIDE_REPEATS to "true"))
        val vm = viewModel(dao)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertTrue("the chip must come back on", vm.state.value.hideRepeats)
    }

    @Test fun flippingTheChipIsRememberedAndTheStateFollows() = runTest {
        val dao = FakeSettingDao()
        val vm = viewModel(dao)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        assertFalse(vm.state.value.hideRepeats)

        vm.setHideRepeats(true)
        runCurrent()

        assertTrue(vm.state.value.hideRepeats)
        assertEquals("true", dao.stored[SettingsRepository.HIDE_REPEATS])
    }
}
