package com.pearlcannon.mixin;

import com.pearlcannon.server.collector.ExplosionDataCollector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pearl Entity tick + setDeltaMovement Mixin
 *
 * 调查历程：
 *   v1.0.5: 注入 Projectile.tick() → HEAD/TAIL 速度相同，采集 0 条
 *   v1.0.6: 注入 Entity.tick() → HEAD/TAIL 速度仍相同（运动更新不在 tick() 内）
 *
 * v1.0.7 方案（双管齐下）：
 *   1. 注入 Entity.tick() HEAD/TAIL：采样每 tick 起止速度
 *   2. 注入 setDeltaMovement：捕获所有速度修改（TNT 爆炸、重力、阻力等）
 *
 *   珍珠速度变化统一通过 setDeltaMovement(Vec3) 修改。
 *   TNT 爆炸助推时，Explosion Wiki: "added to its current velocity"，
 *   即 pearl.setDeltaMovement(current.add(Δv))。
 *   通过 Hook setDeltaMovement，能直接捕获助推前后的速度差。
 *
 *   onSetDeltaMovement 记录"本次 tick 内最后一次 setDeltaMovement 的入参"，
 *   onPearlTickTail 时用该入参与 HEAD 采样对比，反推 Δv。
 */
@Mixin(Entity.class)
public abstract class PearlEntityTickMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHead(CallbackInfo ci) {
        try {
            Entity self = (Entity) (Object) this;
            if (self instanceof ThrownEnderpearl pearl) {
                ExplosionDataCollector.getInstance().onPearlTickHead(pearl);
            }
        } catch (Throwable t) {
            // 静默失败，不影响游戏
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickTail(CallbackInfo ci) {
        try {
            Entity self = (Entity) (Object) this;
            if (self instanceof ThrownEnderpearl pearl) {
                ExplosionDataCollector.getInstance().onPearlTickTail(pearl);
            }
        } catch (Throwable t) {
            // 静默失败，不影响游戏
        }
    }

    @Inject(method = "setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"))
    private void onSetDeltaMovement(Vec3 movement, CallbackInfo ci) {
        try {
            Entity self = (Entity) (Object) this;
            if (self instanceof ThrownEnderpearl pearl) {
                ExplosionDataCollector.getInstance().onPearlSetDeltaMovement(pearl, movement);
            }
        } catch (Throwable t) {
            // 静默失败，不影响游戏
        }
    }
}
