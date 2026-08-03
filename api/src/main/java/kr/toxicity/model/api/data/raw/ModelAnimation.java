/**
 * This source file is part of BetterModel.
 * Copyright (c) 2024–2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */
package kr.toxicity.model.api.data.raw;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.animation.AnimationProgress;
import kr.toxicity.model.api.animation.VectorPoint;
import kr.toxicity.model.api.data.blueprint.AnimationGenerator;
import kr.toxicity.model.api.data.blueprint.BlueprintAnimation;
import kr.toxicity.model.api.data.blueprint.BlueprintAnimator;
import kr.toxicity.model.api.data.blueprint.BlueprintElement;
import kr.toxicity.model.api.script.AnimationScript;
import kr.toxicity.model.api.script.BlueprintScript;
import kr.toxicity.model.api.script.TimeScript;
import kr.toxicity.model.api.util.InterpolationUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static kr.toxicity.model.api.util.CollectionUtil.associate;

/**
 * Represents a raw animation definition from a model file.
 * <p>
 * This record holds the properties of an animation, such as its name, length, loop mode,
 * and the animators that define the keyframes for each bone group.
 * </p>
 *
 * @param name the name of the animation
 * @param loop the loop mode (e.g., play_once, loop, hold)
 * @param override whether this animation should override others
 * @param uuid the unique identifier of the animation
 * @param length the total length of the animation in seconds
 * @param animators a map of animators, keyed by the UUID of the bone group they affect
 * @since 1.15.2
 */
@ApiStatus.Internal
public record ModelAnimation(
    @NotNull String name,
    @Nullable AnimationIterator.Type loop,
    boolean override,
    @NotNull String uuid,
    float length,
    @Nullable Map<String, ModelAnimator> animators
) {
    /**
     * Converts this raw animation data into a processed {@link BlueprintAnimation}.
     *
     * @param context the model loading context
     * @param children the list of root blueprint elements
     * @return the blueprint animation
     * @since 1.15.2
     */
    public @NotNull BlueprintAnimation toBlueprint(
        @NotNull ModelLoadContext context,
        @NotNull List<BlueprintElement> children
    ) {
        var animators = AnimationGenerator.createMovements(length(), children, associate(
            animators().entrySet().stream()
                .filter(e -> context.availableUUIDs.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .filter(ModelAnimator::isAvailable)
                .map(a -> buildAnimationData(context, a)),
            BlueprintAnimator.AnimatorData::name
        ));
        return new BlueprintAnimation(
            name(),
            loop(),
            length(),
            override(),
            animators,
            Optional.ofNullable(animators().get("effects"))
                .filter(ModelAnimator::isNotEmpty)
                .map(a -> toScript(a, context.placeholder))
                .orElseGet(() -> BlueprintScript.fromEmpty(this)),
            animators.isEmpty() ? AnimationProgress.emptyStorage(length()) : animators.values()
                .iterator()
                .next()
                .keyframe()
                .toEmpty()
        );
    }

    private @Nullable BlueprintScript toScript(@NotNull ModelAnimator animator, @NotNull ModelPlaceholder placeholder) {
        var get = animator.stream()
            .map(f -> {
                var lines = effectLines(f, placeholder);
                if (lines.isEmpty()) return null;
                return AnimationScript.of(lines.stream()
                    .map(BetterModel.platform().scriptManager()::build)
                    .filter(Objects::nonNull)
                    .toList()
                ).time(f.time());
            })
            .filter(Objects::nonNull)
            .toList();
        if (get.isEmpty()) return null;
        var list = new ArrayList<TimeScript>(get.size() + 2);
        if (get.getFirst().time() > 0) list.add(TimeScript.EMPTY);
        var before = 0F;
        for (TimeScript timeScript : get) {
            var t = timeScript.time();
            list.add(timeScript.time(InterpolationUtil.roundTime(t - before)));
            before = t;
        }
        var len = InterpolationUtil.roundTime(length() - before);
        if (len > 0) list.add(AnimationScript.EMPTY.time(len));
        return new BlueprintScript(
            name(),
            loop(),
            length(),
            list
        );
    }

    private @NotNull List<String> effectLines(@NotNull ModelKeyframe keyframe, @NotNull ModelPlaceholder placeholder) {
        var point = keyframe.point();
        if (keyframe.channel() == KeyframeChannel.PARTICLE && point.hasEffect()) {
            return List.of(toParticleScript(point));
        }
        if (point.hasScript()) {
            return Arrays.asList(placeholder.parseVariable(point.script()).split("\n"));
        }
        return List.of();
    }

    // Synthesizes a `particle{...}` script line from a Blockbench particle keyframe so it flows through the
    // normal effect-script timeline. Count/offset/speed come from `key=value;` pairs in the keyframe's script
    // field (this fork's convention); non-parameter script text is ignored to keep model loading safe.
    private @NotNull String toParticleScript(@NotNull ModelDatapoint point) {
        var sb = new StringBuilder("particle{effect=").append(point.effect());
        if (point.locator() != null) sb.append(";locator=").append(point.locator());
        var params = point.script();
        if (params != null && !params.isBlank() && Arrays.stream(params.split(";")).allMatch(s -> s.contains("="))) {
            sb.append(';').append(params.trim());
        }
        return sb.append('}').toString();
    }

    /**
     * Returns the loop mode of the animation.
     *
     * @return the loop mode, defaulting to {@link AnimationIterator.Type#PLAY_ONCE} if null
     * @since 1.15.2
     */
    @Override
    public @NotNull AnimationIterator.Type loop() {
        return loop != null ? loop : AnimationIterator.Type.PLAY_ONCE;
    }

    /**
     * Returns the map of animators for this animation.
     *
     * @return the animators, or an empty map if null
     * @since 1.15.2
     */
    @Override
    @NotNull
    public Map<String, ModelAnimator> animators() {
        return animators != null ? animators : Collections.emptyMap();
    }

    @NotNull
    private BlueprintAnimator.AnimatorData buildAnimationData(@NotNull ModelLoadContext context, @NotNull ModelAnimator animator) {
        var position = new ArrayList<VectorPoint>();
        var rotation = new ArrayList<VectorPoint>();
        var scale = new ArrayList<VectorPoint>();
        var version = context.meta.formatVersion();
        animator.stream().filter(keyframe -> keyframe.time() <= length()).forEach(keyframe -> {
            switch (keyframe.channel()) {
                case POSITION -> position.add(keyframe.point(context, version::convertAnimationPosition));
                case ROTATION -> rotation.add(keyframe.point(context, version::convertAnimationRotation));
                case SCALE -> scale.add(keyframe.point(context, version::convertAnimationScale));
            }
        });
        return new BlueprintAnimator.AnimatorData(
            animator.name(),
            position,
            scale,
            rotation,
            animator.rotationGlobal()
        );
    }
}
