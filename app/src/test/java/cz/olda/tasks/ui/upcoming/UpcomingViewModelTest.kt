package cz.olda.tasks.ui.upcoming

import cz.olda.tasks.data.TaskRepository
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class UpcomingViewModelTest {
    @Before fun setMain() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun resetMain() = Dispatchers.resetMain()

    @Test fun isNullUntilTheFirstEmissionThenTheLoadedList() = runTest {
        val clock = Clock.systemDefaultZone()
        val vm = UpcomingViewModel(TaskRepository(FakeTaskDao(emptyList()), clock))
        backgroundScope.launch { vm.state.collect {} }
        assertNull(vm.state.value) // not loaded: must not look like "nothing coming up"
        runCurrent()
        assertEquals(emptyList<Any>(), vm.state.value)
    }
}
