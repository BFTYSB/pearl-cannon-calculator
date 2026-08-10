package com.pearlcannon.mixin;

import com.pearlcannon.server.collector.ExplosionDataCollector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ServerExplosion Mixin - 采集爆炸对珍珠的速度增量
 *
 * 修复：改用「真实速度差」计算 Δv（爆炸后速度 - 爆炸前速度），
 * 替代原先基于几何方向的近似反推，消除因珍珠布局不对称导致的误差。
 *
 * 实现：
 *  - HEAD 注入：在爆炸处理珍珠之前，由采集器快照当前所有无主珍珠速度。
 *  - TAIL 注入：爆炸完成后，由采集器比对快照计算真实 Δv 并记录。
 * 注意：Mixin 不直接引用 EnderPearl / affectedEntities（26.2 中不存在这些 API），
 * 珍珠的查找与速度差计算全部由 ExplosionDataCollector 完成。
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {

    @Inject(method = "explode", at = @At("HEAD"))
    private void onExplosionStart(CallbackInfoReturnable<Integer> cir) {
        try {
            ServerExplosion se = (ServerExplosion) (Object) this;
            ServerLevel level = se.level();
            ExplosionDataCollector.getInstance().beforeExplosion(level);
        } catch (Throwable t) {
            // 静默失败，不影响游戏
        }
    }

    @Inject(method = "explode", at = @At("TAIL"))
    private void onExplosionComplete(CallbackInfoReturnable<Integer> cir) {
        try {
            ServerExplosion se = (ServerExplosion) (Object) this;
            ServerLevel level = se.level();
            ExplosionDataCollector.getInstance().afterExplosion(level, (Explosion) (Object) this);
        } catch (Throwable t) {
            // 静默失败，不影响游戏
        }
    }
}
