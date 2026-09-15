package com.nonono.createbetterwrench.assemble;

import java.util.List;
import java.util.Optional;

import com.nonono.createbetterwrench.BetterWrenchMod;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe.SequencedAssembly;
import com.simibubi.create.foundation.recipe.RecipeApplier;

import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
 *   <li><b>序列装配</b> —— {@code create:sequenced_assembly}, 等价于发射器(Deployer)推进装配。</li>
 *   <li><b>机械手式施加</b> —— {@code create:deploying} 与 {@code create:item_application}。
 *       查找顺序与优先级完全照搬 {@code DeployerBlockEntity#getRecipe}。</li>
 *   <li><b>原木去皮</b> —— 原版 {@link AxeItem} 机制(Create 只在 JEI 里展示这条"隐式配方")。</li>
 *   <li><b>注液</b> —— {@code create:filling}, 等价于注液器(Spout)。</li>
 * </ol>
 *
 * <h2>三个位置</h2>
 * 一个置物台只能渲染一个物品堆, 所以台面**只放"正在加工"的那一个**, 另外两个位置由
 * {@link DepotPiles} 用掉落物实体摆在**置物台的两个对角**:
 * <pre>
 *   原料堆(RAW, 西北角, 锁定期间不可拿)   ←  台面中央(正在加工)  →  成品堆(DONE, 东南角, 可拿)
 * </pre>
 * 消耗掉台面那一个之后会自动从原料堆续上下一个; 装配**失败**的产物直接弹成普通掉落物(不是进货堆)。
 */
public final class AssembleLogic {

    private AssembleLogic() {
    }

    public static boolean tryAssemble(Level level, BlockPos pos, DepotBlockEntity depot,
                                      Player player, ItemStack held, InteractionHand hand) {
        if (held.isEmpty() || held.is(BetterWrenchMod.BETTER_WRENCH))
            return false; // 空手/扳手不参与工作模式(扳手用于锁定/解锁)

        // 台面空了就先从原料堆续一个上来
        if (depot.getHeldItem().isEmpty())
            feedNext(level, pos, depot);
        ItemStack current = depot.getHeldItem();
        if (current.isEmpty())
            return false;

        if (trySequencedAssembly(level, pos, depot, player, held, hand, current))
            return true;
        if (tryApplyingRecipe(level, pos, depot, player, held, hand, current))
            return true;
        if (tryStrippingLog(level, pos, depot, player, held, hand, current))
            return true;
        if (trySpoutFilling(level, pos, depot, player, held, hand, current))
            return true;
        return false;
    }

    // ---------------------------------------------------------------- 1. 序列装配

    private static boolean trySequencedAssembly(Level level, BlockPos pos, DepotBlockEntity depot,
                                                Player player, ItemStack held, InteractionHand hand,
                                                ItemStack current) {
        Optional<RecipeHolder<DeployerApplicationRecipe>> found = SequencedAssemblyRecipe.getRecipe(
            level, current, AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class);
        if (found.isEmpty())
            return false;
        DeployerApplicationRecipe recipe = found.get().value();
        if (!recipe.getRequiredHeldItem().test(held))
            return false;

        // 中间产物身上带着所属装配的 id; 用它查出"这批装配的目标成品"以便区分成品/失败品
        ItemStack target = resolveAssemblyTarget(level, current);

        ItemStack working = current.copyWithCount(1);
        splitExtras(level, pos, depot); // 多余的先挪到原料堆, 台面只留正在加工的那一个

        List<ItemStack> results = RecipeApplier.applyRecipeOn(level, working, recipe, true);
        consumeHeld(player, held, hand, recipe.shouldKeepHeldItem());
        dropExtras(level, pos, results);

        ItemStack out = results.isEmpty() ? ItemStack.EMPTY : results.get(0).copy();
        if (out.isEmpty()) {
            clearDepot(depot);
            return true;
        }

        // 还能继续推进 => 仍是中间产物, 留在中间
        boolean canContinue = SequencedAssemblyRecipe
            .getRecipe(level, out, AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class)
            .isPresent();
        if (canContinue) {
            setDepot(depot, out);
            playPickup(level, pos);
            return true;
        }

        // 序列结束: 结果是按权重从 result 池里抽的 —— 命中目标成品进货堆, 否则算失败品直接弹出
        if (!target.isEmpty() && ItemStack.isSameItemSameComponents(out, target)) {
            finishTo(level, pos, depot, out);
        } else {
            DepotPiles.eject(level, pos, out);
            clearDepot(depot);
        }
        playPickup(level, pos);
        return true;
    }

    // ---------------------------------------------------------------- 2. 机械手式施加

    private static boolean tryApplyingRecipe(Level level, BlockPos pos, DepotBlockEntity depot,
                                             Player player, ItemStack held, InteractionHand hand,
                                             ItemStack current) {
        ItemStackHandler inv = new ItemStackHandler(2);
        inv.setStackInSlot(0, current.copyWithCount(1));
        inv.setStackInSlot(1, held.copyWithCount(1));
        RecipeWrapper wrapper = new RecipeWrapper(inv);

        Optional<RecipeHolder<Recipe<RecipeWrapper>>> found =
            AllRecipeTypes.DEPLOYING.find(wrapper, level).filter(AllRecipeTypes.CAN_BE_AUTOMATED);
        if (found.isEmpty())
            found = AllRecipeTypes.ITEM_APPLICATION.find(wrapper, level).filter(AllRecipeTypes.CAN_BE_AUTOMATED);
        if (found.isEmpty())
            return false;

        Recipe<RecipeWrapper> recipe = found.get().value();
        ItemStack working = current.copyWithCount(1);
        List<ItemStack> results = RecipeApplier.applyRecipeOn(level, working, recipe, true);
        if (results.isEmpty() || results.get(0).isEmpty())
            return false;

        boolean keepHeld = recipe instanceof ItemApplicationRecipe application && application.shouldKeepHeldItem();
        consumeHeld(player, held, hand, keepHeld);
        splitExtras(level, pos, depot);
        dropExtras(level, pos, results);

        finishTo(level, pos, depot, results.get(0).copy());
        playPickup(level, pos);
        return true;
    }

    // ---------------------------------------------------------------- 3. 原木去皮

    private static boolean tryStrippingLog(Level level, BlockPos pos, DepotBlockEntity depot,
                                           Player player, ItemStack held, InteractionHand hand,
                                           ItemStack current) {
        if (!held.is(ItemTags.AXES))
            return false;
        if (!(current.getItem() instanceof BlockItem blockItem))
            return false;

        BlockState stripped = AxeItem.getAxeStrippingState(blockItem.getBlock().defaultBlockState());
        if (stripped == null)
            return false;
        ItemStack out = new ItemStack(stripped.getBlock().asItem());
        if (out.isEmpty())
            return false;

        if (!player.isCreative() && held.getMaxDamage() > 0)
            held.hurtAndBreak(1, player, handSlot(hand));

        splitExtras(level, pos, depot);
        finishTo(level, pos, depot, out);
        level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1f, 1f);
        return true;
    }

    // ---------------------------------------------------------------- 4. 注液

    /**
     * 等价于注液器, 但**按流体实量批量注**:
     * 一份配方可能只吃 25mB(例如发光石), 而玩家手里一个桶是 1000mB,
     * 所以一次操作会把 `1000 / 单份用量` 个物品一起注满(不超过实际可用的输入数量)。
     */
    private static boolean trySpoutFilling(Level level, BlockPos pos, DepotBlockEntity depot,
                                           Player player, ItemStack held, InteractionHand hand,
                                           ItemStack current) {
        if (!GenericItemEmptying.canItemBeEmptied(level, held))
            return false;

        FluidStack available = GenericItemEmptying.emptyItem(level, held.copy(), true).getFirst();
        if (available.isEmpty())
            return false;

        ItemStack probe = current.copyWithCount(1);
        if (!FillingBySpout.canItemBeFilled(level, probe))
            return false;
        int perItem = FillingBySpout.getRequiredAmountForItem(level, probe, available);
        if (perItem <= 0)
            return false;

        // 桶里这 1000mB 够注几份
        int units = available.getAmount() / perItem;
        if (units <= 0)
            return false;

        // 输入 = 台面现有的 + 从原料堆续上来的, 最多凑到 units 个
        int onDepot = current.getCount();
        int wanted = Math.min(units, onDepot + (int) Math.min(Integer.MAX_VALUE, countRaw(level, pos)));
        ItemStack combined = current.copy();
        if (wanted > onDepot) {
            ItemStack extra = DepotPiles.take(level, pos, DepotPiles.RAW, wanted - onDepot);
            if (!extra.isEmpty())
                combined.grow(extra.getCount());
        }
        int toFill = Math.min(units, combined.getCount());
        if (toFill <= 0)
            return false;

        int made = 0;
        for (int i = 0; i < toFill; i++) {
            ItemStack filled = FillingBySpout.fillItem(level, perItem, combined.copyWithCount(1), available.copy());
            if (filled.isEmpty())
                break;
            DepotPiles.deposit(level, pos, DepotPiles.DONE, filled);
            made++;
        }
        if (made <= 0)
            return false;

        // 真正抽掉玩家手里那份流体, 并把空容器还给他
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

        // 没注到的输入退回原料堆, 台面清空后续下一个
        int leftover = combined.getCount() - made;
        if (leftover > 0)
            DepotPiles.deposit(level, pos, DepotPiles.RAW, combined.copyWithCount(leftover));
        clearDepot(depot);
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1f, 1f);
        return true;
    }

    private static long countRaw(Level level, BlockPos pos) {
        // 只要一个上界; 真正的取出由 DepotPiles.take 完成
        return DepotPiles.hasAny(level, pos, DepotPiles.RAW) ? 64L : 0L;
    }

    // ---------------------------------------------------------------- 台面 / 料堆

    /** 台面物品多于 1 个时, 把多余的挪到原料堆; 台面只留 1 个(正在加工的那个)。 */
    private static void splitExtras(Level level, BlockPos pos, DepotBlockEntity depot) {
        ItemStack current = depot.getHeldItem();
        if (current.getCount() <= 1)
            return;
        ItemStack extras = current.copyWithCount(current.getCount() - 1);
        setDepot(depot, current.copyWithCount(1));
        DepotPiles.deposit(level, pos, DepotPiles.RAW, extras);
    }

    /** 成品进货堆, 台面清空后自动从原料堆续上下一个。 */
    private static void finishTo(Level level, BlockPos pos, DepotBlockEntity depot, ItemStack product) {
        DepotPiles.deposit(level, pos, DepotPiles.DONE, product);
        clearDepot(depot);
    }

    private static void feedNext(Level level, BlockPos pos, DepotBlockEntity depot) {
        if (!depot.getHeldItem().isEmpty())
            return;
        ItemStack next = DepotPiles.take(level, pos, DepotPiles.RAW, 1);
        if (next.isEmpty())
            return;
        setDepot(depot, next);
    }

    private static void setDepot(DepotBlockEntity depot, ItemStack stack) {
        depot.setHeldItem(stack);
        // 关键: DepotBlockEntity.setHeldItem 不会自行同步客户端(Create 自己的调用方都会补 notifyUpdate),
        // 不 notify 的话客户端会一直渲染旧物品。
        depot.notifyUpdate();
    }

    private static void clearDepot(DepotBlockEntity depot) {
        setDepot(depot, ItemStack.EMPTY);
    }

    private static void dropExtras(Level level, BlockPos pos, List<ItemStack> results) {
        for (int i = 1; i < results.size(); i++)
            if (!results.get(i).isEmpty())
                Block.popResource(level, pos.above(), results.get(i));
    }

    private static void playPickup(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.25f, 0.75f);
    }

    /** 由中间产物身上携带的 sequenced_assembly 组件查出这批装配的目标成品。 */
    private static ItemStack resolveAssemblyTarget(Level level, ItemStack transitional) {
        SequencedAssembly seq = transitional.get(AllDataComponents.SEQUENCED_ASSEMBLY);
        if (seq == null)
            return ItemStack.EMPTY;
        ResourceLocation id = seq.id();
        return level.getRecipeManager()
            .byKey(id)
            .filter(holder -> holder.value() instanceof SequencedAssemblyRecipe)
            .map(holder -> ((SequencedAssemblyRecipe) holder.value()).resultPool.getFirst().getStack())
            .orElse(ItemStack.EMPTY);
    }

    // ---------------------------------------------------------------- 手持物品处理

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

    private static EquipmentSlot handSlot(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }
}
