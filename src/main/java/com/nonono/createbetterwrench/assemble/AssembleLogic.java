package com.nonono.createbetterwrench.assemble;

import java.util.List;
import java.util.Optional;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.recipe.RecipeApplier;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * 「装配」模式的服务端核心: 把玩家手持物品**施加**到置物台上的物品, 按 Create 的
 * {@code create:sequenced_assembly}(序列装配)推进 —— 等价于"用手代替发射器(Deployer)"。
 *
 * <p>以精密构件为例: 置物台放金板, 依次用 小齿轮→大齿轮→铁粒 右击, 走满 5 轮即得精密构件。</p>
 */
public final class AssembleLogic {

    private AssembleLogic() {
    }

    /**
     * 尝试把 held 施加到置物台物品上。成功(真正推进了一步)返回 true; 否则 false(调用方应保持原状、不取走物品)。
     */
    public static boolean tryAssemble(Level level, BlockPos pos, DepotBlockEntity depot,
                                      Player player, ItemStack held, InteractionHand hand) {
        if (held.isEmpty() || held.is(BetterWrenchMod.BETTER_WRENCH))
            return false; // 空手/扳手不参与装配(扳手用于锁定/解锁)
        ItemStack onDepot = depot.getHeldItem();
        if (onDepot.isEmpty())
            return false;

        // 找到"当前物品 + 下一步施加配方"; 该调用已把 advance() 接到 enforceNextResult 上
        Optional<RecipeHolder<DeployerApplicationRecipe>> found = SequencedAssemblyRecipe.getRecipe(
            level, onDepot, AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class);
        if (found.isEmpty())
            return false;

        DeployerApplicationRecipe recipe = found.get().value();
        if (!recipe.getRequiredHeldItem().test(held))
            return false; // 手上物品不是当前这一步需要的

        // 施加(结果已被序列推进逻辑接管)
        List<ItemStack> results = RecipeApplier.applyRecipeOn(level, onDepot.copyWithCount(1), recipe, true);

        // 消耗手持(支持"工具不消耗"、耐久、余留物; creative 不消耗)
        if (!player.isCreative() && !recipe.shouldKeepHeldItem()) {
            if (held.getMaxDamage() > 0) {
                EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                held.hurtAndBreak(1, player, slot);
            } else {
                ItemStack leftover = held.getCraftingRemainingItem();
                held.shrink(1);
                if (held.isEmpty())
                    player.setItemInHand(hand, leftover);
                else if (!leftover.isEmpty() && !player.getInventory().add(leftover))
                    player.drop(leftover, false);
            }
        }

        // 结果放回置物台; 若有多余产出则掉落
        ItemStack out = results.isEmpty() ? ItemStack.EMPTY : results.get(0).copy();
        depot.setHeldItem(out);
        // 关键: DepotBlockEntity.setHeldItem 不会自行同步客户端(Create 自己的调用方都会补 notifyUpdate),
        // 不 notify 的话客户端会一直渲染旧物品(例如装配完仍显示金板)。
        depot.notifyUpdate();
        for (int i = 1; i < results.size(); i++)
            if (!results.get(i).isEmpty())
                Block.popResource(level, pos.above(), results.get(i));

        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.25f, 0.75f);
        return true;
    }
}
