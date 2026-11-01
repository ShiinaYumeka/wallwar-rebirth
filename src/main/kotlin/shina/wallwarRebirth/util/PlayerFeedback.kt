package shina.wallwarRebirth.util

import net.minecraft.core.Holder
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
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

fun ServerPlayer.showTitle(text: Component, fadeIn: Int = 5, stay: Int = 40, fadeOut: Int = 10) {
    connection.send(ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut))
    connection.send(ClientboundSetSubtitleTextPacket(Component.empty()))
    connection.send(ClientboundSetTitleTextPacket(text))
}

fun ServerPlayer.clearTitle() {
    connection.send(ClientboundClearTitlesPacket(true))
}

fun ServerPlayer.showActionBar(text: Component) {
    displayClientMessage(text, true)
}
