package shina.wallwarRebirth.monster

import eu.pb4.polymer.core.api.entity.PolymerEntity
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.Vex
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.rules.MatchRules
import xyz.nucleoid.packettweaker.PacketContext

class SummonedVex(type: EntityType<out SummonedVex>, level: Level) :
    Vex(type, level),
    PolymerEntity,
    SummonedUnit {
    override var kindId: String = VEX_ID
    override var summonedTeam: String = ""
    override val mind: SummonedBrain = SummonedBrain(this, this)

    init {
        setPersistenceRequired()
        refreshName()
    }

    override fun getPolymerEntityType(context: PacketContext): EntityType<*> = EntityType.VEX

    override fun registerGoals() {
        super.registerGoals()
        targetSelector.removeAllGoals { true }
    }

    fun inheritFrom(owner: SummonedUnit, bound: net.minecraft.core.BlockPos?) {
        summonedTeam = owner.summonedTeam
        kindId = VEX_ID
        mind.bindHome(owner.mind.homePos() ?: bound)
        setOwner(owner as Mob)
        if (bound != null) setBoundOrigin(bound)
        addTag(MatchRules.SUMMONER_TAG)
        refreshName()
    }

    fun refreshName() {
        val server = (level() as? ServerLevel)?.server
        val scoreTeam = if (summonedTeam.isEmpty() || server == null) {
            null
        } else {
            server.scoreboard.getPlayerTeam(summonedTeam)
        }
        if (server != null) {
            if (scoreTeam != null) {
                server.scoreboard.addPlayerToTeam(scoreboardName, scoreTeam)
            } else {
                server.scoreboard.removePlayerFromTeam(scoreboardName)
            }
        }
        val color = scoreTeam?.color ?: ChatFormatting.WHITE
        val shown = if (color == ChatFormatting.RESET) ChatFormatting.WHITE else color
        customName = Component.literal("恼鬼").withStyle { style ->
            style.withColor(shown).withItalic(false)
        }
        isCustomNameVisible = true
    }

    override fun removeWhenFarAway(distance: Double): Boolean = false

    override fun getBaseExperienceReward(level: ServerLevel): Int = 0

    override fun wantsToPickUp(level: ServerLevel, stack: ItemStack): Boolean = false

    override fun remove(reason: RemovalReason) {
        SummonedUnits.remove(this)
        super.remove(reason)
    }

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
        val hurt = super.hurtServer(level, source, amount)
        if (hurt && amount > 0f) mind.onDamaged(level, source)
        return hurt
    }

    override fun doHurtTarget(level: ServerLevel, target: Entity): Boolean {
        val hit = super.doHurtTarget(level, target)
        if (hit) mind.noteExchange()
        return hit
    }

    override fun dropCustomDeathLoot(level: ServerLevel, source: DamageSource, recentlyHit: Boolean) {}

    override fun customServerAiStep(level: ServerLevel) {
        if (Match.frozen(level)) return
        super.customServerAiStep(level)
        shina.wallwarRebirth.monster.ai.VexAi.tick(this, level)
        val home = mind.homeSite(level)
        if (home != null && home.holding) {
            setBoundOrigin(home.blockPos)
        }
    }

    override fun addAdditionalSaveData(output: net.minecraft.world.level.storage.ValueOutput) {
        super.addAdditionalSaveData(output)
        mind.save(output)
    }

    override fun readAdditionalSaveData(input: net.minecraft.world.level.storage.ValueInput) {
        super.readAdditionalSaveData(input)
        mind.load(input)
        refreshName()
    }

    companion object {
        const val VEX_ID = "vex"
    }
}
