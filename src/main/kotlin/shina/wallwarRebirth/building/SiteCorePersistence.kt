package shina.wallwarRebirth.building

import com.mojang.serialization.Codec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.SimpleContainer
import java.util.UUID

internal fun SiteCoreBlockEntity.rememberCovered(pos: BlockPos, state: BlockState) {
    if (coveredBelow.any { it.pos == pos }) return
    coveredBelow.add(CoveredBlock(pos, state))
}

internal fun SiteCoreBlockEntity.restoreCovered(world: ServerLevel) {
    if (coveredBelow.isEmpty()) return
    val pending = coveredBelow.toList()
    coveredBelow.clear()
    for ((pos, state1) in pending) {
        if (world.isOutsideBuildHeight(pos)) continue
        val state = world.getBlockState(pos)
        val cleared = state.isAir ||
            state.block is SiteStructureBlock ||
            state.block is SitePartBlock ||
            state.block is SiteCoreBlock
        if (!cleared) continue
        world.setBlock(
            pos,
            state1,
            Block.UPDATE_ALL,
        )
    }
}

internal fun readIds(input: ValueInput, key: String, into: MutableList<String>) {
    val ids = input.read(key, Codec.STRING.listOf()).orElse(emptyList()) ?: return
    for (id in ids) {
        if (id != null) into.add(id)
    }
}

internal fun SiteCoreBlockEntity.readContainer(input: ValueInput, key: String, container: SimpleContainer) {
    container.clearContent()
    val items = input.read(key, ItemStack.OPTIONAL_CODEC.listOf()).orElse(emptyList()) ?: return
    items.forEachIndexed { slot, stack ->
        if (stack.isEmpty) return@forEachIndexed
        if (slot < container.containerSize) container.setItem(slot, stack)
        else deferredDrops.add(stack.copy())
    }
}

internal fun SiteCoreBlockEntity.writeState(output: ValueOutput) {
    output.putString("Building", buildingId)
    output.putInt("Level", buildingLevel)
    output.putInt("Health", health)
    output.putString("Team", team)
    output.putString("Facing", facing)
    output.putInt("Age", age)
    output.putInt("FallAge", fallAge)
    output.putInt("ActionAge", actionAge)
    output.putInt("WeaponCooldown", weaponCooldown)
    output.putString("ProducedMonster", producedMonster)
    output.putBoolean("Marching", marching)
    output.putBoolean("CommandPost", commandPost)
    output.putString("CommandOwner", commandOwner?.toString() ?: "")
    output.putBoolean("Holding", holding)
    output.putInt("HoldEpoch", holdEpoch)
    output.putBoolean("HasHoldAnchor", holdAnchor != null)
    holdAnchor?.let { anchor ->
        output.putInt("HoldAnchorX", anchor.x)
        output.putInt("HoldAnchorY", anchor.y)
        output.putInt("HoldAnchorZ", anchor.z)
    }
    output.putInt("EggReleaseAge", eggReleaseAge)
    output.putString("HealthDisplay", healthDisplayId?.toString() ?: "")
    output.putString("Crossbow", crossbowId?.toString() ?: "")
    output.putString("ReinforceMineral", reinforceMineral)
    output.putString("ReinforceTwoMineral", reinforceTwoMineral)
    output.store("Reinforce", ItemStack.OPTIONAL_CODEC, reinforce)
    output.store("ReinforceTwo", ItemStack.OPTIONAL_CODEC, reinforceTwo)
    output.store("Diffuse", ItemStack.OPTIONAL_CODEC, diffuse)
    output.putString("Gunner", gunner?.toString() ?: "")
    output.putInt("HealAge", healAge)
    output.putInt("ReleaseCount", releaseCount)
    output.putInt("LastHurtAge", lastHurtAge)
    output.putLong("WallHealedGameTime", wallHealedGameTime)
    output.putInt("DripAge", dripAge)
    output.store("BarracksStock", Codec.STRING.listOf(), barracksStock.toList())
    output.store("DripQueue", Codec.STRING.listOf(), dripQueue.toList())
    output.store("Parts", Codec.LONG.listOf(), partPositions.map { it.asLong() })
    output.store("CoveredBelow", CoveredBlock.CODEC.listOf(), coveredBelow.toList())
    output.store("MineralAges", Codec.unboundedMap(Codec.STRING, Codec.INT), mineralAges)
    output.putBoolean("CrystalFurnaceOn", crystalFurnaceOn)
    output.putInt("CrystalFurnaceTicks", crystalFurnaceTicks)
    output.putInt("CrystalFurnaceIron", crystalFurnaceIron)
    output.putInt("CrystalFurnaceCopper", crystalFurnaceCopper)
    val items = (0 until storage.containerSize).map { storage.getItem(it) }
    output.store("Storage", ItemStack.OPTIONAL_CODEC.listOf(), items)
    output.store(
        "BarracksMaterials",
        ItemStack.OPTIONAL_CODEC.listOf(),
        (0 until barracksMaterials.containerSize).map { barracksMaterials.getItem(it) },
    )
    output.store(
        "BarracksEggs",
        ItemStack.OPTIONAL_CODEC.listOf(),
        (0 until barracksEggs.containerSize).map { barracksEggs.getItem(it) },
    )
}

internal fun SiteCoreBlockEntity.readState(input: ValueInput) {
    buildingId = input.getStringOr("Building", "")
    buildingLevel = input.getIntOr("Level", 0)
    health = input.getIntOr("Health", -1)
    if (health < 0) {
        health = Buildings.byId(buildingId)?.maxHealth(buildingLevel) ?: 0
    }
    if (buildingId == Buildings.TESLA.id) {
        health = health.coerceAtMost(Buildings.TESLA.maxHealth(buildingLevel))
    }
    team = input.getStringOr("Team", "")
    facing = input.getStringOr("Facing", Direction.SOUTH.serializedName)
    age = input.getIntOr("Age", 0)
    fallAge = input.getIntOr("FallAge", 0)
    actionAge = input.getIntOr("ActionAge", 0)
    weaponCooldown = input.getIntOr("WeaponCooldown", 0)
    producedMonster = input.getStringOr("ProducedMonster", "zombie")
    marching = input.getBooleanOr("Marching", true)
    commandPost = input.getBooleanOr("CommandPost", false)
    commandOwner = input.getStringOr("CommandOwner", "").takeIf { it.isNotEmpty() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    holding = input.getBooleanOr("Holding", false)
    holdEpoch = input.getIntOr("HoldEpoch", 0)
    holdAnchor = if (input.getBooleanOr("HasHoldAnchor", false)) {
        BlockPos(
            input.getIntOr("HoldAnchorX", 0),
            input.getIntOr("HoldAnchorY", 0),
            input.getIntOr("HoldAnchorZ", 0),
        )
    } else {
        null
    }
    eggReleaseAge = input.getIntOr("EggReleaseAge", 0)
    healthDisplayId = input.getStringOr("HealthDisplay", "").takeIf { it.isNotEmpty() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    crossbowId = input.getStringOr("Crossbow", "").takeIf { it.isNotEmpty() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    reinforceMineral = input.getStringOr("ReinforceMineral", "")
    reinforceTwoMineral = input.getStringOr("ReinforceTwoMineral", "")
    reinforce = input.read("Reinforce", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY) ?: ItemStack.EMPTY
    reinforceTwo = input.read("ReinforceTwo", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY) ?: ItemStack.EMPTY
    diffuse = input.read("Diffuse", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY) ?: ItemStack.EMPTY
    gunner = input.getStringOr("Gunner", "").takeIf { it.isNotEmpty() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    healAge = input.getIntOr("HealAge", 0)
    releaseCount = input.getIntOr("ReleaseCount", 0)
    lastHurtAge = input.getIntOr("LastHurtAge", -1)
    wallHealedGameTime = input.getLongOr("WallHealedGameTime", -1L)
    dripAge = input.getIntOr("DripAge", 0)
    barracksStock.clear()
    readIds(input, "BarracksStock", barracksStock)
    dripQueue.clear()
    readIds(input, "DripQueue", dripQueue)
    partPositions.clear()
    val parts = input.read("Parts", Codec.LONG.listOf()).orElse(emptyList()) ?: emptyList()
    for (packed in parts) {
        if (packed != null) partPositions.add(BlockPos.of(packed))
    }
    coveredBelow.clear()
    val covered = input.read("CoveredBelow", CoveredBlock.CODEC.listOf()).orElse(emptyList()) ?: emptyList()
    for (block in covered) {
        if (block != null) coveredBelow.add(block)
    }
    mineralAges.clear()
    val ages = input.read("MineralAges", Codec.unboundedMap(Codec.STRING, Codec.INT)).orElse(emptyMap()) ?: emptyMap()
    for ((name, ticks) in ages) {
        if (name != null && ticks != null) mineralAges[name] = ticks
    }
    crystalFurnaceOn = input.getBooleanOr("CrystalFurnaceOn", false)
    crystalFurnaceTicks = input.getIntOr("CrystalFurnaceTicks", 0)
    crystalFurnaceIron = input.getIntOr("CrystalFurnaceIron", 0)
    crystalFurnaceCopper = input.getIntOr("CrystalFurnaceCopper", 0)
    val items = input.read("Storage", ItemStack.OPTIONAL_CODEC.listOf()).orElse(emptyList())
    if (items != null) {
        items.forEachIndexed { slot, stack ->
            if (slot < storage.containerSize) storage.setItem(slot, stack)
        }
    }
    readContainer(input, "BarracksMaterials", barracksMaterials)
    readContainer(input, "BarracksEggs", barracksEggs)
}

