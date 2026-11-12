package shina.wallwarRebirth.monster

import com.mojang.serialization.Dynamic
import eu.pb4.polymer.core.api.entity.PolymerEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.Brain
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.npc.villager.Villager
import net.minecraft.world.entity.npc.villager.VillagerProfession
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import shina.wallwarRebirth.match.Match
import xyz.nucleoid.packettweaker.PacketContext

class SummonedVillager(type: EntityType<out SummonedVillager>, level: Level) : Villager(type, level), PolymerEntity, SummonedUnit {
    override var kindId: String = Monsters.VILLAGER.id
    override var summonedTeam: String = ""
    override val mind: SummonedBrain = SummonedBrain(this, this)
    var workAge: Int = 0

    init {
        setPersistenceRequired()
        age = 0
    }

    override fun getPolymerEntityType(context: PacketContext): EntityType<*> = EntityType.VILLAGER

    override fun registerGoals() {
        goalSelector.addGoal(1, FloatGoal(this))
    }

    override fun makeBrain(dynamic: Dynamic<*>): Brain<*> = brainProvider().makeBrain(dynamic)

    override fun refreshBrain(level: ServerLevel) {
        val current = getBrain()
        current.stopAll(level, this)
        this.brain = current.copyWithoutBehaviors()
    }

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
        val hurt = super.hurtServer(level, source, amount)
        forgetPanic()
        return hurt
    }

    override fun setLastHurtByMob(attacker: net.minecraft.world.entity.LivingEntity?) {
        super.setLastHurtByMob(attacker)
        forgetPanic()
    }

    fun forgetPanic() {
        val mind = getBrain()
        mind.eraseMemory(MemoryModuleType.HURT_BY)
        mind.eraseMemory(MemoryModuleType.HURT_BY_ENTITY)
        mind.eraseMemory(MemoryModuleType.IS_PANICKING)
        mind.eraseMemory(MemoryModuleType.PATH)
        mind.eraseMemory(MemoryModuleType.WALK_TARGET)
        mind.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
    }

    fun unemployed(): Boolean = villagerData.profession().`is`(VillagerProfession.NONE)

    fun isFarmer(): Boolean = villagerData.profession().`is`(VillagerProfession.FARMER)

    fun becomeFarmer() {
        val world = level() as? ServerLevel ?: return
        setVillagerData(villagerData.withProfession(world.registryAccess(), VillagerProfession.FARMER))
        customName = net.minecraft.network.chat.Component.literal("唤灵·农民").withStyle { style ->
            val shown = (world.server.scoreboard.getPlayerTeam(summonedTeam)?.color
                ?: net.minecraft.ChatFormatting.WHITE).let { color ->
                if (color == net.minecraft.ChatFormatting.RESET) net.minecraft.ChatFormatting.WHITE else color
            }
            style.withColor(shown).withItalic(false)
        }
        isCustomNameVisible = true
    }

    fun takeWheat(): Boolean = inventory.addItem(ItemStack(Items.WHEAT)).isEmpty

    override fun mobInteract(player: Player, hand: InteractionHand): InteractionResult = InteractionResult.PASS

    override fun removeWhenFarAway(distance: Double): Boolean = false

    override fun getBaseExperienceReward(level: ServerLevel): Int = 0

    override fun wantsToPickUp(level: ServerLevel, stack: ItemStack): Boolean = false

    override fun canBreed(): Boolean = false

    override fun thunderHit(level: ServerLevel, lightning: net.minecraft.world.entity.LightningBolt) {}

    override fun remove(reason: RemovalReason) {
        SummonedUnits.remove(this)
        super.remove(reason)
    }

    override fun dropCustomDeathLoot(level: ServerLevel, source: DamageSource, recentlyHit: Boolean) {}

    override fun customServerAiStep(level: ServerLevel) {
        if (Match.frozen(level)) return
        shina.wallwarRebirth.monster.ai.VillagerAi.tick(this, level)
    }

    override fun addAdditionalSaveData(output: net.minecraft.world.level.storage.ValueOutput) {
        super.addAdditionalSaveData(output)
        output.putInt("WorkAge", workAge)
        mind.save(output)
    }

    override fun readAdditionalSaveData(input: net.minecraft.world.level.storage.ValueInput) {
        super.readAdditionalSaveData(input)
        workAge = input.getIntOr("WorkAge", 0)
        mind.load(input)
        SummonedGear.apply(this, this)
    }
}
