/**
 * This source file is part of BetterModel.
 * Copyright (c) 2024–2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */
package kr.toxicity.model.bukkit.compatibility.citizens.command

import kr.toxicity.model.api.BetterModel
import kr.toxicity.model.api.animation.AnimationIterator
import kr.toxicity.model.api.animation.AnimationModifier
import kr.toxicity.model.api.profile.ModelProfile
import kr.toxicity.model.api.profile.ModelProfileInfo
import kr.toxicity.model.api.tracker.TrackerModifier
import kr.toxicity.model.bukkit.util.wrap
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.command.Arg
import net.citizensnpcs.api.command.Command
import net.citizensnpcs.api.command.CommandContext
import net.citizensnpcs.api.npc.NPC
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

class LimbCommand {
    @Command(
        aliases = ["npc"],
        usage = "limb <id> <model> <animation> [loop_type] [skin]",
        desc = "",
        modifiers = ["limb"],
        min = 4,
        max = 6,
        permission = "citizens.npc.animate"
    )
    @Suppress("UNUSED")
    fun animate(
        args: CommandContext,
        sender: CommandSender,
        npc: NPC?,
        @Arg(1) id: String,
        @Arg(2) model: String,
        @Arg(3) animation: String,
        @Arg(4) type: String?,
        @Arg(5) skin: String?
    ) {
        val targetNpc = CitizensAPI.getNPCRegistry().getById(id.toIntOrNull() ?: return) ?: return
        val npcEntity = targetNpc.entity?.wrap() ?: return
        val skinProfile = skin?.let { value ->
            if (value.length > 16) runCatching {
                ModelProfile.of(ModelProfileInfo.UNKNOWN, BetterModel.platform().profileManager().skin(value)).asUncompleted()
            }.getOrNull()
            else Bukkit.getPlayerExact(value)?.let { ModelProfile.of(it.wrap()).asUncompleted() }
                ?: ModelProfile.of(Bukkit.getOfflinePlayer(value).wrap())
        }

        val animType = type
            ?.let { value ->
                runCatching {
                    AnimationIterator.Type.valueOf(value.uppercase())
                }.getOrNull()
            }
            ?: AnimationIterator.Type.PLAY_ONCE

        BetterModel.limb(model)
            .map { renderer ->
                if (skinProfile != null) {
                    renderer.getOrCreate(npcEntity, skinProfile, TrackerModifier.DEFAULT)
                } else {
                    renderer.getOrCreate(npcEntity, TrackerModifier.DEFAULT)
                }
            }
            .ifPresent { tracker ->
                val success = tracker.animate(
                    animation,
                    AnimationModifier.builder()
                        .start(0)
                        .type(animType)
                        .build()
                ) {
                    tracker.close()
                }
                if (!success) tracker.close()
            }
    }
}
