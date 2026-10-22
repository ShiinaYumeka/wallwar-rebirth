package shina.wallwarRebirth.util

import net.minecraft.core.Holder
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource

fun ServerPlayer.playNotifySound(
    event: SoundEvent,
    source: SoundSource,
    volume: Float,
    pitch: Float,
) {
    val packet = ClientboundSoundEntityPacket(
        Holder.direct(event),
        source,
        this,
        volume,
        pitch,
        random.nextLong(),
    )
    connection.send(packet)
}
