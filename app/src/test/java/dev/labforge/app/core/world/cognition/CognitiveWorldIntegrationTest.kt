package dev.labforge.app.core.world.cognition

import dev.labforge.app.core.world.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CognitiveWorldIntegrationTest {
 @get:Rule val temp=TemporaryFolder()
 @Test fun ternaryStateSpaceAndUrgentSystemOneDecision() {
  val world=WorldFactory.create(nowWallClockMs=1L)
  val agent=world.agents.first().copy(needs=Needs(energy=4f,nutrition=70f,social=60f,motivation=60f,satisfaction=60f,stress=10f))
  val decision=CognitiveEngine().decideFast(CognitiveInput(agent,world,600L))
  assertEquals(ActionType.SLEEP,decision.action)
  assertTrue(decision.stateCode in 0 until BalancedTernaryState.STATE_COUNT)
 }
 @Test fun innieAndOutieMemoriesAreIsolated() {
  val world=WorldFactory.create(nowWallClockMs=1L);val a=world.agents.first()
  val office=world.buildings.first{it.kind==BuildingKind.OFFICE};val square=world.buildings.first{it.kind==BuildingKind.PUBLIC_SQUARE}
  val manager=SeveranceMemoryManager(temp.newFolder())
  val innie=a.copy(currentBuildingId=office.id);val work=world.copy(agents=world.agents.map{if(it.id==a.id)innie else it})
  manager.remember(innie,work,"confidential research project",sourceKey="work")
  val outie=a.copy(currentBuildingId=square.id);val life=world.copy(agents=world.agents.map{if(it.id==a.id)outie else it})
  manager.remember(outie,life,"friends at public square",sourceKey="life")
  assertTrue(manager.recall(innie,work,"research").all{it.memory.mode==MemoryMode.INNIE})
  assertTrue(manager.recall(outie,life,"friends").all{it.memory.mode==MemoryMode.OUTIE})
 }
 @Test fun highStressSwitchesToOutieAtWork() {
  val world=WorldFactory.create(nowWallClockMs=1L);val a=world.agents.first();val office=world.buildings.first{it.kind==BuildingKind.OFFICE}
  val stressed=a.copy(currentBuildingId=office.id,needs=a.needs.copy(stress=95f))
  val state=world.copy(agents=world.agents.map{if(it.id==a.id)stressed else it})
  assertEquals(MemoryMode.OUTIE,SeveranceMemoryManager(temp.newFolder()).currentMode(stressed,state))
 }
 @Test fun soulProfileCanBePersistedAndReloaded() {
  val dir=temp.newFolder();val manager=SeveranceMemoryManager(dir)
  manager.updateSoul("agent_1"){it.absorb("curiosity",true)}
  val reloaded=SeveranceMemoryManager(dir)
  assertEquals(1L,reloaded.soul("agent_1").experiences)
  assertTrue(reloaded.soul("agent_1").values.getValue("curiosity")>.5f)
 }
}
