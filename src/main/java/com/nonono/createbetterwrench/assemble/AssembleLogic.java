package com.nonono.createbetterwrench.assemble;

import java.util.List;
import java.util.Optional;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.recipe.RecipeApplier;

import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

/**
 * 「工作」(内部 id 仍为 {@code assemble})模式的服务端核心: 在**已锁定的置物台**上,
 * 用手代替机器, 依次尝试四种原版机制(全部走 Create 自己的配方体系, 不硬编码具体配方):
 *
 * <ol>
 *   <li><b>序列装配</b> —— {@code create:sequenced_assembly}, 等价于发射器(Deployer)推进装配。
 *       例: 金板 + 小齿轮/大齿轮/铁粒 ×5 轮 → 精密构件。</li>
 *   <li><b>机械手式施加</b> —— {@code create:deploying} 与 {@code create:item_application},
 *       把手上物品施加到台面物品上。例: 蜂蜜/斧子给铜块上蜡去蜡、皮革包 cardboard 等。
 *       查找顺序与优先级完全照搬 {@code DeployerBlockEntity#getRecipe}。</li>
 *   <li><b>原木去皮</b> —— 原版 {@link AxeItem} 机制(原版里斧子右键原木方块即去皮)。
 *       Create 自己只在 JEI 里展示这条"隐式配方"({@code LogStrippingFakeRecipes}),
 *       并未生成真实配方, 所以这里直接按原版机制实现。</li>
 *   <li><b>注液</b> —— {@code create:filling}, 等价于注液器(Spout):
 *       从**手持容器**里取出流体注入台面物品。例: 岩浆桶 → 烈焰蛋糕。</li>
 * </ol>
 *
 * <p>四者按上述顺序短路: 任一成功即返回, 不再尝试后面的。</p>
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
            return false; // 空手/扳手不参与工作模式(扳手用于锁定/解锁)
        ItemStack onDepot = depot.getHeldItem();
        if (onDepot.isEmpty())
            return false;

        if (trySequencedAssembly(level, pos, depot, player, held, hand, onDepot))
            return true;
        if (tryApplyingRecipe(level, pos, depot, player, held, hand, onDepot))
            return true;
        if (tryStrippingLog(level, pos, depot, player, held, hand, onDepot))
            return true;
        if (trySpoutFilling(level, pos, depot, player, held, hand, onDepot))
            return true;
        return false;
    }

    // ---------------------------------------------------------------- 1. 序列装配

    private static boolean trySequencedAssembly(Level level, BlockPos pos, DepotBlockEntity depot,
                                                Player player, ItemStack held, InteractionHand hand,
                                                ItemStack onDepot) {
        // 找到"当前物品 + 下一步施加配方"; 该调用已把 advance() 接到 enforceNextResult 上
        Optional<RecipeHolder<DeployerApplicationRecipe>> found = SequencedAssemblyRecipe.getRecipe(
            level, onDepot, AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class);
        if (found.isEmpty())
            return false;

        DeployerApplicationRecipe recipe = found.get().value();
        if (!recipe.getRequiredHeldItem().test(held))
            return false; // 手上物品不是当前这一步需要的

        List<ItemStack> results = RecipeApplier.applyRecipeOn(level, onDepot.copyWithCount(1), recipe, true);
        consumeHeld(player, held, hand, recipe.shouldKeepHeldItem());
        placeResults(level, pos, depot, results);
        return true;
    }

    // ---------------------------------------------------------------- 2. 机械手式施加

    /**
     * 与 {@code DeployerBlockEntity#getRecipe} 同源的查找: 输入槽 0 = 台面物品, 槽 1 = 手持物品。
     * 依次尝试 {@code create:deploying} 与 {@code create:item_application}, 并过滤掉
     * {@code *_manual_only}(Create 约定: 这类配方只能由玩家在世界里手动对**方块**使用)。
     */
    private static boolean tryApplyingRecipe(Level level, BlockPos pos, DepotBlockEntity depot,
                                             Player player, ItemStack held, InteractionHand hand,
                                             ItemStack onDepot) {
        ItemStackHandler inv = new ItemStackHandler(2);
        inv.setStackInSlot(0, onDepot.copyWithCount(1));
        inv.setStackInSlot(1, held.copyWithCount(1));
        RecipeWrapper wrapper = new RecipeWrapper(inv);

        Optional<RecipeHolder<Recipe<RecipeWrapper>>> found =
            AllRecipeTypes.DEPLOYING.find(wrapper, level)
                .filter(AllRecipeTypes.CAN_BE_AUTOMATED);
        if (found.isEmpty())
            found = AllRecipeTypes.ITEM_APPLICATION.find(wrapper, level)
                .filter(AllRecipeTypes.CAN_BE_AUTOMATED);
        if (found.isEmpty())
            return false;

        Recipe<RecipeWrapper> recipe = found.get().value();
        List<ItemStack> results = RecipeApplier.applyRecipeOn(level, onDepot.copyWithCount(1), recipe, true);
        if (results.isEmpty())
            return false;

        boolean keepHeld = recipe instanceof ItemApplicationRecipe application && application.shouldKeepHeldItem();
        consumeHeld(player, held, hand, keepHeld);
        placeResults(level, pos, depot, results);
        return true;
    }

    // ---------------------------------------------------------------- 3. 原木去皮

    private static boolean tryStrippingLog(Level level, BlockPos pos, DepotBlockEntity depot,
                                           Player player, ItemStack held, InteractionHand hand,
                                           ItemStack onDepot) {
        if (!held.is(ItemTags.AXES))
            return false;
        if (!(onDepot.getItem() instanceof BlockItem blockItem))
            return false;

        BlockState stripped = AxeItem.getAxeStrippingState(blockItem.getBlock().defaultBlockState());
        if (stripped == null)
            return false;
        ItemStack out = new ItemStack(stripped.getBlock().asItem());
        if (out.isEmpty())
            return false;

        if (!player.isCreative() && held.getMaxDamage() > 0)
            held.hurtAndBreak(1, player, handSlot(hand));

        depot.setHeldItem(out);
        depot.notifyUpdate();
        level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1f, 1f);
        return true;
    }

    // ---------------------------------------------------------------- 4. 注液

    /**
     * 等价于注液器: 从**手持容器**中抽出流体, 注入台面物品。
     * 流体来源用 Create 的 {@link GenericItemEmptying#emptyItem} —— 它同时覆盖
     * {@code create:emptying} 配方与 NeoForge 的 {@code Capabilities.FluidHandler.ITEM}(桶、Create 流体罐等)。
     */
    private static boolean trySpoutFilling(Level level, BlockPos pos, DepotBlockEntity depot,
                                           Player player, ItemStack held, InteractionHand hand,
                                           ItemStack onDepot) {
        if (!GenericItemEmptying.canItemBeEmptied(level, held))
            return false;

        // 先空跑一次, 只为拿到"这一格容器里到底是什么流体"
        FluidStack available = GenericItemEmptying.emptyItem(level, held.copy(), true).getFirst();
        if (available.isEmpty())
            return false;
        if (!FillingBySpout.canItemBeFilled(level, onDepot))
            return false;

        int amount = FillingBySpout.getRequiredAmountForItem(level, onDepot, available);
        if (amount <= 0 || available.getAmount() < amount)
            return false;

        ItemStack filled = FillingBySpout.fillItem(level, amount, onDepot.copyWithCount(1), available.copy());
        if (filled.isEmpty())
            return false;

        // 真正抽掉玩家手里的流体, 并把空容器还给他
        if (!player.isCreative()) {
            Pair<FluidStack, ItemStack> drained = GenericItemEmptying.emptyItem(level, held.copy(), false);
            ItemStack container = drained.getSecond();
            if (held.getCount() <= 1) {
                player.setItemInHand(hand, container.isEmpty() ? ItemStack.EMPTY : container);
            } else {
                held.shrink(1);
                if (!container.isEmpty() && !player.getInventory().add(container))
                    player.drop(container, false);
            }
        }

        depot.setHeldItem(filled);
        depot.notifyUpdate();
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 1f);
        return true;
    }

    // ---------------------------------------------------------------- 公共

    /** 消耗/损耗手持物品(支持"工具不消耗"、耐久、余留物;创造模式不消耗)。 */
    private static void consumeHeld(Player player, ItemStack held, InteractionHand hand, boolean keepHeld) {
        if (player.isCreative() || keepHeld)
            return;
        if (held.getMaxDamage() > 0) {
            held.hurtAndBreak(1, player, handSlot(hand));
            return;
        }
        ItemStack leftover = held.getCraftingRemainingItem();
        held.shrink(1);
        if (held.isEmpty())
            player.setItemInHand(hand, leftover);
        else if (!leftover.isEmpty() && !player.getInventory().add(leftover))
            player.drop(leftover, false);
    }

    /** 结果放回置物台; 第 2 个起的多余产出掉落在台面上方。 */
    private static void placeResults(Level level, BlockPos pos, DepotBlockEntity depot, List<ItemStack> results) {
        ItemStack out = results.isEmpty() ? ItemStack.EMPTY : results.get(0).copy();
        depot.setHeldItem(out);
        // 关键: DepotBlockEntity.setHeldItem 不会自行同步客户端(Create 自己的调用方都会补 notifyUpdate),
        // 不 notify 的话客户端会一直渲染旧物品(例如装配完仍显示金板)。
        depot.notifyUpdate();
        for (int i = 1; i < results.size(); i++)
            if (!results.get(i).isEmpty())
                Block.popResource(level, pos.above(), results.get(i));

        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.25f, 0.75f);
    }

    private static EquipmentSlot handSlot(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }
}
