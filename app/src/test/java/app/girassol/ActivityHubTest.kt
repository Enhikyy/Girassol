package app.girassol

import app.girassol.core.activity.ActivityHub
import app.girassol.core.activity.ActivityKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ActivityHubTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun realOperationMovesToZoneAndPersistsCompletion() {
        val file = File(folder.root, "history.json")
        val hub = ActivityHub(file)
        val id = hub.begin(ActivityKind.RESEARCH, detail = "Consultando fontes")
        hub.progress(id, 0.5f, "Lendo")
        assertEquals("library", hub.state.value.primary?.kind?.zoneId)
        assertEquals(0.5f, hub.state.value.primary?.progress)
        hub.complete(id, "3 fontes")
        assertNull(hub.state.value.primary)
        assertEquals("3 fontes", hub.state.value.recentEvents.single().detail)
        assertTrue(ActivityHub(file).state.value.recentEvents.single().successful)
    }

    @Test fun failuresRemainVisibleInHistory() {
        val hub = ActivityHub(File(folder.root, "history.json"))
        val id = hub.begin(ActivityKind.BUILD)
        hub.fail(id, "Gradle falhou")
        assertEquals(ActivityKind.DIAGNOSIS.zoneId, "clinic")
        assertEquals(false, hub.state.value.recentEvents.single().successful)
    }
}
