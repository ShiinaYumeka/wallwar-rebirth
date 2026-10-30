package shina.wallwarRebirth.building

import eu.pb4.polymer.core.api.entity.PolymerEntity
import net.minecraft.core.BlockPos
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import xyz.nucleoid.packettweaker.PacketContext

class BuildingHealthLabel(type: EntityType<out BuildingHealthLabel>, level: Level) :
    Display.TextDisplay(type, level),
    PolymerEntity {
    var buildingTeam: String = ""
    var currentHealth: Int = 0
    var maxHealth: Int = 0
    var corePos: BlockPos = BlockPos.ZERO
    private var sentHealth: Int = -1
    private var sentMax: Int = -1

    init {
        billboardConstraints = BillboardConstraints.CENTER
        backgroundColor = 0x80000000.toInt()
        lineWidth = 400
    }

    fun refresh() {
        if (currentHealth == sentHealth && maxHealth == sentMax) return
        text = HealthBars.component(currentHealth, maxHealth, net.minecraft.ChatFormatting.WHITE)
        sentHealth = currentHealth
        sentMax = maxHealth
    }

    override fun getPolymerEntityType(context: PacketContext): EntityType<*> = EntityType.TEXT_DISPLAY

    override fun modifyRawTrackedData(
        data: MutableList<SynchedEntityData.DataValue<*>>,
        player: ServerPlayer,
        initial: Boolean,
    ) {
        val index = data.indexOfFirst { (it.value as? net.minecraft.network.chat.Component)?.string?.contains("/") == true }
        if (index < 0) return
        val old = data[index]
        val colored = HealthBars.component(currentHealth, maxHealth, HealthBars.color(player, buildingTeam))
        @Suppress("UNCHECKED_CAST")
        val serializer = old.serializer() as net.minecraft.network.syncher.EntityDataSerializer<net.minecraft.network.chat.Component>
        data[index] = SynchedEntityData.DataValue(old.id, serializer, colored)
    }

    override fun addAdditionalSaveData(output: ValueOutput) {
        super.addAdditionalSaveData(output)
        output.putString("BuildingTeam", buildingTeam)
        output.putInt("CurrentHealth", currentHealth)
        output.putInt("MaxHealth", maxHealth)
        output.putInt("CoreX", corePos.x)
        output.putInt("CoreY", corePos.y)
        output.putInt("CoreZ", corePos.z)
    }

    override fun readAdditionalSaveData(input: ValueInput) {
        super.readAdditionalSaveData(input)
        buildingTeam = input.getStringOr("BuildingTeam", "")
        currentHealth = input.getIntOr("CurrentHealth", 0)
        maxHealth = input.getIntOr("MaxHealth", 0)
        corePos = BlockPos(input.getIntOr("CoreX", 0), input.getIntOr("CoreY", 0), input.getIntOr("CoreZ", 0))
        sentHealth = -1
        sentMax = -1
    }
}
