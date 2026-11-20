package shina.wallwarRebirth.building

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import shina.wallwarRebirth.item.BuildingCoreItem
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.shop.match.TeamWarehouses

object SuperWeapons {
    fun ids(): Set<String> = setOf(Buildings.GUIDED_CANNON.id, Buildings.TELEPORT_SPIRE.id)

    fun isSuperWeapon(id: String): Boolean = id in ids()

    fun cooldownTicks(definition: BuildingDefinition, level: Int): Int {
        val index = (level - 1).coerceIn(0, 2)
        return when (definition.id) {
            Buildings.GUIDED_CANNON.id -> MatchRules.GUIDED_CANNON_COOLDOWN[index]
            Buildings.TELEPORT_SPIRE.id -> MatchRules.TELEPORT_SPIRE_COOLDOWN[index]
            else -> 0
        }
    }

    fun ready(site: SiteCoreBlockEntity): Boolean = site.weaponCooldown <= 0

    fun tickCooldown(site: SiteCoreBlockEntity) {
        if (Match.phase != Match.Phase.FIGHT) return
        if (site.weaponCooldown <= 0) return
        site.weaponCooldown--
        if (site.weaponCooldown % 20 == 0) site.setChanged()
    }

    fun startCooldown(site: SiteCoreBlockEntity) {
        val definition = Buildings.byId(site.buildingId) ?: return
        site.weaponCooldown = cooldownTicks(definition, site.buildingLevel)
        site.setChanged()
    }

    fun placed(level: ServerLevel, team: String, buildingId: String, ignore: SiteCoreBlockEntity? = null): Boolean {
        if (team.isEmpty()) return false
        return SiteCores.all().any { core ->
            core !== ignore &&
                core.level === level &&
                !core.isRemoved &&
                core.buildingId == buildingId &&
                core.team == team &&
                core.health > 0
        }
    }

    fun teamOwns(
        level: ServerLevel,
        team: String,
        buildingId: String,
        ignoreStack: ItemStack? = null,
        ignoreSite: SiteCoreBlockEntity? = null,
    ): Boolean {
        if (team.isEmpty()) return false
        if (placed(level, team, buildingId, ignoreSite)) return true
        return hasUnplacedCore(level, team, buildingId, ignoreStack)
    }

    fun hasUnplacedCore(
        level: ServerLevel,
        team: String,
        buildingId: String,
        ignoreStack: ItemStack? = null,
    ): Boolean {
        if (team.isEmpty()) return false
        val players = level.server.playerList.players
        for (player in players) {
            if ((player.team?.name ?: "") != team) continue
            if (inventoryHas(player.inventory, buildingId, ignoreStack)) return true
            if (isCore(player.containerMenu.carried, buildingId) && player.containerMenu.carried !== ignoreStack) return true
        }
        val box = TeamWarehouses.of(team) ?: return false
        for (index in 0 until box.containerSize) {
            val stack = box.getItem(index)
            if (stack !== ignoreStack && isCore(stack, buildingId)) return true
        }
        return false
    }

    private fun inventoryHas(inventory: Inventory, buildingId: String, ignore: ItemStack?): Boolean {
        for (index in 0 until inventory.containerSize) {
            val stack = inventory.getItem(index)
            if (stack !== ignore && isCore(stack, buildingId)) return true
        }
        return false
    }

    fun isCore(stack: ItemStack, buildingId: String): Boolean {
        if (stack.isEmpty) return false
        val item = stack.item as? BuildingCoreItem ?: return false
        return item.coreBuildingId() == buildingId
    }
}
