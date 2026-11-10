package app.girassol

import app.girassol.core.activity.ActivityKind
import app.girassol.world.WorldZones
import org.junit.Assert.assertEquals
import org.junit.Test

class WorldMappingTest {
    @Test fun everyActivityHasARealWorldZone() {
        ActivityKind.entries.forEach { kind ->
            assertEquals(kind.zoneId, WorldZones.byId(kind.zoneId).id)
        }
        assertEquals(6, WorldZones.all.map { it.id }.distinct().size)
    }
}
