package shina.wallwarRebirth.monster

import net.minecraft.world.item.Items

object Monsters {
    val ZOMBIE = MonsterDefinition(
        id = "zombie",
        displayName = "唤灵·僵尸",
        rank = 1,
        health = 40.0,
        armor = 2.0,
        playerAttack = 3.0,
        buildingDamage = 1,
        buildingIntervalTicks = 20,
        attackRange = 2.0,
        dropItem = Items.REDSTONE,
        dropCount = 1,
    )

    fun byId(id: String): MonsterDefinition? = if (id == ZOMBIE.id) ZOMBIE else null
}
