package shina.wallwarRebirth.building

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Containers
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import shina.wallwarRebirth.match.Match
import shina.wallwarRebirth.rules.MatchRules
import shina.wallwarRebirth.shop.match.TeamWarehouses

object CrystalFurnaceWork {
    fun reset(site: SiteCoreBlockEntity) {
        site.crystalFurnaceOn = false
        clearBatch(site)
    }

    fun tick(site: SiteCoreBlockEntity, level: ServerLevel) {
        if (site.buildingId != Buildings.CRYSTAL_FURNACE.id) return
        if (site.health <= 0) return
        if (!Match.isFight()) {
            if (hasBatch(site)) abortAndDrop(site, level)
            return
        }
        if (hasBatch(site)) {
            site.crystalFurnaceTicks--
            if (site.crystalFurnaceTicks <= 0) {
                complete(site, level)
            }
            site.setChanged()
            return
        }
        if (site.crystalFurnaceOn) tryStart(site)
    }

    fun toggle(site: SiteCoreBlockEntity, player: ServerPlayer) {
        if (site.crystalFurnaceOn) {
            site.crystalFurnaceOn = false
            site.setChanged()
            return
        }
        if (!Match.isFight()) {
            player.sendSystemMessage(Component.literal("只有战斗阶段能开启"))
            return
        }
        site.crystalFurnaceOn = true
        site.setChanged()
    }

    fun infoLines(site: SiteCoreBlockEntity): List<String> {
        val lines = ArrayList<String>()
        lines.add(if (site.crystalFurnaceOn) "已开启" else "已关闭")
        if (hasBatch(site)) {
            val seconds = (
                site.crystalFurnaceTicks + MatchRules.TICKS_PER_SECOND - 1
            ) / MatchRules.TICKS_PER_SECOND
            lines.add("转化剩余 ${seconds.coerceAtLeast(1)} 秒")
        }
        return lines
    }

    fun abortAndDrop(site: SiteCoreBlockEntity, level: ServerLevel) {
        if (!hasBatch(site)) {
            clearBatch(site)
            return
        }
        val pos = site.blockPos
        if (site.crystalFurnaceIron > 0) {
            Containers.dropItemStack(
                level,
                pos.x + 0.5,
                pos.y + 0.5,
                pos.z + 0.5,
                ItemStack(Items.IRON_INGOT, site.crystalFurnaceIron),
            )
        }
        if (site.crystalFurnaceCopper > 0) {
            Containers.dropItemStack(
                level,
                pos.x + 0.5,
                pos.y + 0.5,
                pos.z + 0.5,
                ItemStack(Items.COPPER_INGOT, site.crystalFurnaceCopper),
            )
        }
        clearBatch(site)
        site.setChanged()
    }

    internal fun chooseBatch(iron: Int, copper: Int): Pair<Int, Int>? {
        val size = MatchRules.CRYSTAL_FURNACE_BATCH_SIZE
        val share = MatchRules.CRYSTAL_FURNACE_EQUAL_SHARE
        if (iron + copper < size) return null
        if (iron >= share && copper >= share) return share to share
        val minIron = (size - copper).coerceAtLeast(0)
        val maxIron = iron.coerceAtMost(size)
        val takeIron = share.coerceIn(minIron, maxIron)
        return takeIron to (size - takeIron)
    }

    private fun tryStart(site: SiteCoreBlockEntity) {
        val iron = TeamWarehouses.count(site.team, Items.IRON_INGOT)
        val copper = TeamWarehouses.count(site.team, Items.COPPER_INGOT)
        val batch = chooseBatch(iron, copper) ?: return
        if (!TeamWarehouses.tryDeduct(
                site.team,
                listOf(
                    Items.IRON_INGOT to batch.first,
                    Items.COPPER_INGOT to batch.second,
                ),
            )
        ) {
            return
        }
        site.crystalFurnaceIron = batch.first
        site.crystalFurnaceCopper = batch.second
        site.crystalFurnaceTicks = MatchRules.CRYSTAL_FURNACE_CONVERT_TICKS
        site.setChanged()
    }

    private fun complete(site: SiteCoreBlockEntity, level: ServerLevel) {
        val index = (site.buildingLevel - 1).coerceIn(0, MatchRules.CRYSTAL_FURNACE_SUCCESS_RATE.lastIndex)
        val rate = MatchRules.CRYSTAL_FURNACE_SUCCESS_RATE[index]
        val product = if (level.random.nextInt(100) < rate) {
            ItemStack(Items.DIAMOND, MatchRules.CRYSTAL_FURNACE_SUCCESS_DIAMOND)
        } else if (level.random.nextBoolean()) {
            ItemStack(Items.REDSTONE, MatchRules.CRYSTAL_FURNACE_FAIL_REDSTONE)
        } else {
            ItemStack(Items.LAPIS_LAZULI, MatchRules.CRYSTAL_FURNACE_FAIL_LAPIS)
        }
        TeamWarehouses.acceptAll(site.team, product)
        clearBatch(site)
        site.setChanged()
    }

    private fun hasBatch(site: SiteCoreBlockEntity): Boolean =
        site.crystalFurnaceTicks > 0 && (site.crystalFurnaceIron > 0 || site.crystalFurnaceCopper > 0)

    private fun clearBatch(site: SiteCoreBlockEntity) {
        site.crystalFurnaceTicks = 0
        site.crystalFurnaceIron = 0
        site.crystalFurnaceCopper = 0
    }
}
