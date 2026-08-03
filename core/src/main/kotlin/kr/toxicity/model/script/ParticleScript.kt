/**
 * This source file is part of BetterModel.
 * Copyright (c) 2024–2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */
package kr.toxicity.model.script

import kr.toxicity.model.api.script.AnimationScript
import kr.toxicity.model.api.tracker.Tracker
import kr.toxicity.model.util.PLATFORM

/**
 * Emits a vanilla particle at a keyframe time, from an optional locator bone, to the tracker's viewers only.
 */
class ParticleScript(
    private val effect: String,
    private val locator: String?,
    private val count: Int,
    private val offsetX: Double,
    private val offsetY: Double,
    private val offsetZ: Double,
    private val speed: Double
) : AnimationScript {

    override fun accept(tracker: Tracker) {
        val base = tracker.location()
        val loc = locator?.let { tracker.bone(it) }?.worldPosition()?.let { wp ->
            base.add(wp.x.toDouble(), wp.y.toDouble(), wp.z.toDouble())
        } ?: base
        tracker.pipeline.allPlayer().forEach { player ->
            PLATFORM.spawnParticle(player, effect, loc.x(), loc.y(), loc.z(), count, offsetX, offsetY, offsetZ, speed)
        }
    }

    override fun isSync(): Boolean = true
}
