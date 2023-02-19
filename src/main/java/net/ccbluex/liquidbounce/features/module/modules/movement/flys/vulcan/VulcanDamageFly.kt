package net.ccbluex.liquidbounce.features.module.modules.movement.flys.vulcan

import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.flys.FlyMode
import net.ccbluex.liquidbounce.features.value.BoolValue
import net.ccbluex.liquidbounce.features.value.FloatValue
import net.ccbluex.liquidbounce.utils.MovementUtils
import net.ccbluex.liquidbounce.utils.PacketUtils
import net.ccbluex.liquidbounce.utils.ClientUtils
import net.minecraft.client.settings.GameSettings
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C03PacketPlayer.C04PacketPlayerPosition
import net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook
import net.minecraft.network.play.client.C0FPacketConfirmTransaction
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import kotlin.math.sqrt

class VulcanDamageFly : FlyMode("VulcanDamage") {
    private val onlyDamageValue = BoolValue("${valuePrefix}OnlyDamage", true)
    private val selfDamageValue = BoolValue("${valuePrefix}SelfDamage", true)

    private var waitFlag = false
    private var isStarted = false
    //Tips: for some reason Vulcan detects InstantDamage(Motion C/D). If you want to fly with InstantDamage, bind Damage and Fly together
    //注意：Vulcan会检测瞬间自伤（因为少了Jump Achievement，可能以后会考虑加上），所以要是想瞬间自伤直接飞的话，可以选择搭高或者把Fly和Damage绑一起，然后关闭SelfDamage选项
    var isDamaged = false
    var dmgJumpCount = 0
    var flyTicks = 0
    
    fun runSelfDamageCore(): Boolean {
        mc.timer.timerSpeed = 1.0f
        if (!onlyDamageValue.get() || !selfDamageValue.get()) {
            if (onlyDamageValue.get()) {
                if (mc.thePlayer.hurtTime > 0 || isDamaged) {
                    isDamaged = true
                    dmgJumpCount = 999
                    return false
                }else {
                    return true
                }
            }
            isDamaged = true
            dmgJumpCount = 999
            return false
        }
        if (isDamaged) {
            dmgJumpCount = 999
            return false
        }
        mc.thePlayer.jumpMovementFactor = 0.00f
        if (mc.thePlayer.onGround) {
            if (dmgJumpCount >= 4) {
                mc.netHandler.addToSendQueue(C04PacketPlayerPosition(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, true))
                isDamaged = true
                dmgJumpCount = 999
                return false
            }
            dmgJumpCount++
            MovementUtils.resetMotion(true)
            mc.thePlayer.jump()
        }
        MovementUtils.resetMotion(false)
        return true
    }

    override fun onEnable() {
        flyTicks = 0
        waitFlag = false
        isStarted = false
        isDamaged = false
        dmgJumpCount = 0
        mc.timer.timerSpeed = 1.0f
        runSelfDamageCore()
    }

    override fun onUpdate(event: UpdateEvent) {
        if (runSelfDamageCore()) {
            return
        }
        mc.thePlayer.jumpMovementFactor = 0.00f
        MovementUtils.resetMotion(true)
        if (!isStarted && !waitFlag) {
            mc.netHandler.addToSendQueue(C04PacketPlayerPosition(mc.thePlayer.posX, mc.thePlayer.posY - 0.0784, mc.thePlayer.posZ, false))
            waitFlag = true
        }
        if (isStarted) {
            flyTicks++
            if (flyTicks > 4) {
                flyTicks = 4
            }
            MovementUtils.strafe((0.96 + Math.random() / 50).toFloat())
        }
    }

    override fun onDisable() {
        mc.timer.timerSpeed = 1.0f
        MovementUtils.resetMotion(true)
    }

    override fun onPacket(event: PacketEvent) {
        val packet = event.packet
        if (packet is C03PacketPlayer && waitFlag) { //Cancel C03 when waiting phase flag, make sure you can fly for 10s (10x C03)
            event.cancelEvent()
        }
        if (packet is C03PacketPlayer && (dmgJumpCount < 4 && selfDamageValue.get())) {
            packet.onGround = false
        }
        if (isStarted) {
            if(packet is C03PacketPlayer && (packet is C04PacketPlayerPosition || packet is C06PacketPlayerPosLook)) {
                val deltaX = packet.x - lastSentX
                val deltaY = packet.y - lastSentY
                val deltaZ = packet.z - lastSentZ

                if (sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) > 9.5) {
                    lastSentX = packet.x
                    lastSentY = packet.y
                    lastSentZ = packet.z
                    flyTicks++
                    return
                }
                event.cancelEvent()
            }else if(packet is C03PacketPlayer) {
                event.cancelEvent()
            }
        }
        if (packet is S08PacketPlayerPosLook && waitFlag) {
            isStarted = true
            waitFlag = false
            mc.timer.timerSpeed = 1.0f
            flyTicks = 0
        }
        if (packet is C0FPacketConfirmTransaction) { //Make sure it works with Vulcan Combat Disabler
            val transUID = (packet.uid).toInt()
            if (transUID >= -31767 && transUID <= -30769) {
                event.cancelEvent()
                PacketUtils.sendPacketNoEvent(packet)
            }
        }
    }
}
