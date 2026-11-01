package shina.wallwarRebirth.building

import com.mojang.math.Transformation
import eu.pb4.polymer.core.api.entity.PolymerEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.Mth
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import java.util.UUID
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import xyz.nucleoid.packettweaker.PacketContext
import kotlin.math.atan2

class TowerCrossbow(type: EntityType<out TowerCrossbow>, level: Level) :
    Display.ItemDisplay(type, level),
    PolymerEntity {
    var corePos: BlockPos = BlockPos.ZERO

    init {
        billboardConstraints = BillboardConstraints.FIXED
        itemTransform = ItemDisplayContext.NONE
        setTransformation(LAY_FLAT)
        shadowRadius = 0f
        setItemStack(ItemStack(Items.CROSSBOW))
        setPosRotInterpolationDuration(2)
    }

    override fun getPolymerEntityType(context: PacketContext): EntityType<*> = EntityType.ITEM_DISPLAY

    override fun addAdditionalSaveData(output: ValueOutput) {
        super.addAdditionalSaveData(output)
        output.putInt("CoreX", corePos.x)
        output.putInt("CoreY", corePos.y)
        output.putInt("CoreZ", corePos.z)
    }

    override fun readAdditionalSaveData(input: ValueInput) {
        super.readAdditionalSaveData(input)
        corePos = BlockPos(input.getIntOr("CoreX", 0), input.getIntOr("CoreY", 0), input.getIntOr("CoreZ", 0))
        if (itemStack.isEmpty) setItemStack(ItemStack(Items.CROSSBOW))
        billboardConstraints = BillboardConstraints.FIXED
        itemTransform = ItemDisplayContext.NONE
        setTransformation(LAY_FLAT)
        shadowRadius = 0f
    }

    companion object {
        private lateinit var type: EntityType<TowerCrossbow>

        fun bind(registered: EntityType<TowerCrossbow>) {
            type = registered
        }

        fun create(level: Level): TowerCrossbow = TowerCrossbow(type, level)

        private val LAY_FLAT = Transformation(
            Vector3f(0f, 0f, 0f),
            Quaternionf().rotateX(Math.toRadians(90.0).toFloat()),
            Vector3f(1f, 1f, 1f),
            Quaternionf(),
        )
    }
}

interface CrossbowSite {
    val buildingId: String
    var crossbowId: UUID?
    fun facingDirection(): Direction
    fun crossbowPos(): BlockPos
    fun crossbowLevel(): Level?
}

object TowerCrossbows {
    fun ensure(site: CrossbowSite, level: ServerLevel) {
        if (site.buildingId != Buildings.TOWER.id) {
            discard(site)
            return
        }
        shown(site, level)
    }

    fun aim(site: CrossbowSite, level: ServerLevel, target: LivingEntity?) {
        val crossbow = shown(site, level)
        val yaw = yawOf(site, target) ?: return
        if (Mth.degreesDifferenceAbs(crossbow.yRot, yaw) < 1f) return
        crossbow.yRot = yaw
    }

    fun discard(site: CrossbowSite) {
        val level = site.crossbowLevel() as? ServerLevel
        if (level != null) {
            site.crossbowId?.let { id -> level.getEntity(id)?.discard() }
            val box = AABB(site.crossbowPos()).inflate(0.5, 1.5, 0.5)
            for (entity in level.getEntitiesOfClass(TowerCrossbow::class.java, box)) {
                if (entity.corePos == site.crossbowPos()) entity.discard()
            }
        }
        site.crossbowId = null
    }

    private fun shown(site: CrossbowSite, level: ServerLevel): TowerCrossbow {
        val known = site.crossbowId?.let { level.getEntity(it) as? TowerCrossbow }
        if (known != null && !known.isRemoved) return known
        val box = AABB(site.crossbowPos()).inflate(0.5, 1.5, 0.5)
        val nearby = level.getEntitiesOfClass(TowerCrossbow::class.java, box)
            .firstOrNull { it.corePos == site.crossbowPos() && !it.isRemoved }
        if (nearby != null) {
            site.crossbowId = nearby.uuid
            return nearby
        }
        val crossbow = TowerCrossbow.create(level)
        val pos = site.crossbowPos()
        crossbow.corePos = pos
        val yaw = site.facingDirection().toYRot()
        crossbow.snapTo(pos.x + 0.5, pos.y + 1.02, pos.z + 0.5, yaw, 0f)
        level.addFreshEntity(crossbow)
        site.crossbowId = crossbow.uuid
        return crossbow
    }

    private fun yawOf(site: CrossbowSite, target: LivingEntity?): Float? {
        if (target == null) return null
        val origin = Vec3.atCenterOf(site.crossbowPos())
        val dx = target.x - origin.x
        val dz = target.z - origin.z
        if (dx * dx + dz * dz < 1.0e-8) return null
        return Math.toDegrees(atan2(-dx, dz)).toFloat()
    }
}
